package io.wispforest.owo.ui.util;

import io.wispforest.owo.ui.core.CursorStyle;
import net.minecraft.client.Minecraft;

public class CursorAdapter {

    protected CursorStyle lastCursorStyle = CursorStyle.POINTER;
    protected boolean disposed = false;

    protected CursorAdapter() {}

    public static CursorAdapter ofClientWindow() {
        return new CursorAdapter();
    }

    public void applyStyle(CursorStyle style) {
        if (this.disposed || this.lastCursorStyle == style) return;

        Minecraft.getInstance().getWindow().selectCursor(style.cursor);
        this.lastCursorStyle = style;
    }

    public void dispose() {
        if (this.disposed) return;

        this.disposed = true;
    }

}
