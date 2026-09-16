package io.wispforest.owo.braid.core.cursor;

import org.lwjgl.sdl.SDLMouse;

public final class SystemCursorStyle implements CursorStyle {
    public final int systemCursorId;

    SystemCursorStyle(int systemCursorId) {
        this.systemCursorId = systemCursorId;
    }

    @Override
    public long allocate() {
        return SDLMouse.SDL_CreateSystemCursor(this.systemCursorId);
    }
}
