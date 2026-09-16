package io.wispforest.owo.mixin.braid;

import com.mojang.blaze3d.platform.Window;
import io.wispforest.owo.braid.core.BraidWindow;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {

    @Shadow
    public long handle() {
        throw new AssertionError();
    }

    @Inject(method = "handleEvent", at = @At("HEAD"), cancellable = true)
    private void routeBraidWindowEvent(SDL_Event event, CallbackInfo ci) {
        if (SDLEvents.SDL_GetWindowFromEvent(event) != this.handle() && BraidWindow.dispatchEvent(event)) {
            ci.cancel();
        }
    }
}
