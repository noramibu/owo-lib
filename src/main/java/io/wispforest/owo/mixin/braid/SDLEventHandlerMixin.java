package io.wispforest.owo.mixin.braid;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.SDLEventHandler;
import io.wispforest.owo.braid.core.BraidWindow;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SDLEventHandler.class)
public class SDLEventHandlerMixin {

    @Inject(
        method = "pollEvents",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/SDLEventHandler;handleDropBeginEvent()V")
    )
    private void routeBraidWindowDropBegin(CallbackInfo ci, @Local SDL_Event event) {
        BraidWindow.dispatchEvent(event);
    }

    @Inject(method = {
        "handleKeyEvent",
        "handleTextInputEvent",
        "handleTextEditingEvent",
        "handleMouseMotionEvent",
        "handleMouseButtonEvent",
        "handleMouseWheelEvent",
        "handleDropFileEvent",
        "handleDropCompleteEvent"
    }, at = @At("HEAD"), cancellable = true)
    private void routeBraidWindowInput(SDL_Event event, CallbackInfo ci) {
        if (BraidWindow.dispatchEvent(event)) {
            ci.cancel();
        }
    }
}
