package io.wispforest.owo.braid.core;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.device.SurfaceException;
import io.wispforest.owo.Owo;
import io.wispforest.owo.braid.core.cursor.CursorController;
import io.wispforest.owo.braid.core.cursor.CursorStyle;
import io.wispforest.owo.braid.core.events.*;
import io.wispforest.owo.braid.framework.widget.Widget;
import io.wispforest.owo.braid.util.BraidGuiRenderer;
import io.wispforest.owo.mixin.braid.MinecraftAccessor;
import io.wispforest.owo.mixin.braid.RenderSystemAccessor;
import io.wispforest.owo.util.EventSource;
import io.wispforest.owo.util.EventStream;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

// TODO: consider somehow getting notified or polling
//       for changes in the gui scale option so we can react
//       instantly when it changes rather than on next resize
public class BraidWindow implements Surface {

    public final EventBinding eventBinding = new WindowEventBinding(this);

    public final Window backendWindow;
    private final GpuSurface windowSurface;

    private static final Map<Long, BraidWindow> WINDOWS = new HashMap<>();

    private final EventStream<ResizeCallback> onResize = ResizeCallback.newStream();
    private TextureTarget remoteTarget;

    public final BraidGuiRenderer guiRenderer;

    private final CursorController cursorController;
    private final List<Path> droppedFiles = new ArrayList<>();

    private int scaleFactor;

    public BraidWindow(String title, int width, int height) {
        this.backendWindow = new Window(
            new WindowEventHandler() {
                @Override
                public void framebufferSizeChanged() {
                    remoteTarget.destroyBuffers();
                    remoteTarget = new TextureTarget("braid window", backendWindow.getWidth(), backendWindow.getHeight(), GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);

                    onResize.sink().onResize(backendWindow.getGuiScaledWidth(), backendWindow.getGuiScaledHeight());

                    resizeSurface();
                }

                @Override
                public void resizeGui() {}

                @Override
                public void cursorEntered() {}

                @Override
                public void fullscreenStateChanged(boolean fullscreen) {}
            },
            new DisplayData(width, height, OptionalInt.empty(), OptionalInt.empty(), false),
            null,
            false,
            title,
            ((MinecraftAccessor) Minecraft.getInstance()).owo$getMonitorManager(),
            RenderSystemAccessor.owo$getBackend());

        SDLVideo.SDL_ShowWindow(this.backendWindow.handle());
        WINDOWS.put(this.backendWindow.handle(), this);
        this.backendWindow.setWindowCloseCallback(() -> this.eventBinding.add(CloseEvent.INSTANCE));

        this.windowSurface = RenderSystem.getDevice().createSurface(this.backendWindow.handle(), this.backendWindow::isIconified);

        this.cursorController = new CursorController(this.backendWindow.handle());
        this.guiRenderer = new BraidGuiRenderer(Minecraft.getInstance());

        this.remoteTarget = new TextureTarget("braid window", this.backendWindow.getWidth(), this.backendWindow.getHeight(), GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        this.resizeSurface();
    }

    private void resizeSurface() {
        try {
            this.windowSurface.configure(new GpuSurface.Configuration(
                this.backendWindow.getWidth(),
                this.backendWindow.getHeight(),
                GpuSurface.PresentMode.getSupportedVsyncMode(this.windowSurface.supportedPresentModes(), false)
            ));
        } catch (SurfaceException e) {
            Owo.LOGGER.warn("Failed to resize braid window");
        }
        this.recalculateScale();
    }

    public static boolean dispatchEvent(SDL_Event event) {
        var window = WINDOWS.get(SDLEvents.SDL_GetWindowFromEvent(event));
        if (window == null) return false;

        window.handleEvent(event);
        return true;
    }

    private void handleEvent(SDL_Event event) {
        switch (event.type()) {
            case 768, 769 -> {
                var key = event.key();
                var modifiers = new KeyModifiers(key.mod());
                this.eventBinding.add(event.type() == 769
                    ? new KeyReleaseEvent(key.scancode(), key.key(), modifiers)
                    : new KeyPressEvent(key.scancode(), key.key(), modifiers));
            }
            case 770 -> {}
            case 771 -> {
                var text = event.text().textString();
                if (text != null) {
                    var modifiers = new KeyModifiers(SDLKeyboard.SDL_GetModState());
                    text.codePoints().forEach(codepoint -> this.eventBinding.add(new CharInputEvent((char) codepoint, modifiers)));
                }
            }
            case 1024 -> {
                var motion = event.motion();
                this.eventBinding.add(new MouseMoveEvent(motion.x() / this.scaleFactor, motion.y() / this.scaleFactor));
            }
            case 1025, 1026 -> {
                var button = event.button();
                var modifiers = new KeyModifiers(SDLKeyboard.SDL_GetModState());
                this.eventBinding.add(event.type() == 1025
                    ? new MouseButtonPressEvent(button.button(), modifiers)
                    : new MouseButtonReleaseEvent(button.button(), modifiers));
            }
            case 1027 -> {
                var wheel = event.wheel();
                this.eventBinding.add(new MouseScrollEvent(wheel.x(), wheel.y()));
            }
            case 4098 -> this.droppedFiles.clear();
            case 4096 -> {
                var rawPath = event.drop().dataString();
                if (rawPath != null) {
                    try {
                        this.droppedFiles.add(Paths.get(rawPath));
                    } catch (InvalidPathException e) {
                        Owo.LOGGER.error("Failed to parse path '{}'", rawPath, e);
                    }
                }
            }
            case 4099 -> {
                if (!this.droppedFiles.isEmpty()) {
                    this.eventBinding.add(new FilesDroppedEvent(List.copyOf(this.droppedFiles)));
                    this.droppedFiles.clear();
                }
            }
            default -> this.backendWindow.handleEvent(event);
        }
    }

    private void recalculateScale() {
        var guiScale = Minecraft.getInstance().options.guiScale().get();
        var forceUnicodeFont = Minecraft.getInstance().options.forceUnicodeFont().get();

        this.scaleFactor = this.backendWindow.calculateScale(guiScale, forceUnicodeFont);
        this.backendWindow.setGuiScale(scaleFactor);
    }

    public static OpenResult open(String title, int width, int height, Widget widget) {
        var window = new BraidWindow(title, width, height);
        var app = new AppState(
            Owo.LOGGER,
            AppState.formatName("BraidWindow", widget, title),
            Minecraft.getInstance(),
            window,
            window.eventBinding,
            widget
        );

        BraidWindowScheduler.add(window, app);
        return new OpenResult(app, window);
    }

    // ---

    @Override
    public void dispose() {
        WINDOWS.remove(this.backendWindow.handle());
        this.windowSurface.close();
        this.backendWindow.close();
        this.cursorController.dispose();

        this.guiRenderer.close();

        this.remoteTarget.destroyBuffers();
    }

    // ---

    @Override
    public int width() {
        return this.backendWindow.getGuiScaledWidth();
    }

    @Override
    public int height() {
        return this.backendWindow.getGuiScaledHeight();
    }

    @Override
    public double scaleFactor() {
        return this.scaleFactor;
    }

    @Override
    public EventSource<ResizeCallback> onResize() {
        return this.onResize.source();
    }

    @Override
    public CursorStyle currentCursorStyle() {
        return this.cursorController.currentStyle();
    }

    @Override
    public void setCursorStyle(CursorStyle style) {
        this.cursorController.setStyle(style);
    }

    // ---

    @Override
    public void beginRendering() {
        try {
            this.windowSurface.acquireNextTexture();
        } catch (SurfaceException e) {
            Owo.LOGGER.warn("Failed to acquire texture");
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            this.remoteTarget.getColorTexture(),
            new Vector4f(0, 0, 0, 1),
            this.remoteTarget.getDepthTexture(),
            1
        );
    }

    @Override
    public void endRendering() {
        this.guiRenderer.render(new BraidGuiRenderer.Target(
            this.remoteTarget,
            this
        ));

        // ---

        if (!this.windowSurface.isAcquired()) return;

        this.windowSurface.blitFromTexture(
            RenderSystem.getDevice().createCommandEncoder(),
            this.remoteTarget.getColorTextureView()
        );
        RenderSystem.getDevice().createCommandEncoder().submit();
        this.windowSurface.present();
    }

    // ---

    public static class WindowEventBinding extends EventBinding {

        public final BraidWindow window;

        public WindowEventBinding(BraidWindow window) {
            this.window = window;
        }

        @Override
        public boolean isKeyPressed(int keyCode) {
            return InputConstants.isKeyDown(keyCode);
        }
    }

    public record OpenResult(AppState state, BraidWindow window) {}
}
