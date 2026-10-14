package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;

/** Reference-inspired graphite palette; no bright accents or vanilla button textures. */
public final class UiTheme {
    public static final int TEXT = 0xFFE4E4E4;
    public static final int MUTED = 0xFFA0A0A0;
    public static final int QUIET = 0xFF777777;
    public static final int PANEL = 0xF21A1A1A;
    public static final int BORDER = 0xFF2A2A2A;
    public static final int ROW = 0xFF252525;
    public static final int ROW_HOVER = 0xFF303030;
    public static final int ACTIVE = 0xFF303030;
    public static final int ACTIVE_BORDER = 0xFF464646;
    public static final int FOCUS = 0xFF999999;

    private UiTheme() { }

    public static void fill(UiCanvas canvas, Rect bounds, int color) {
        if (bounds.width() > 0 && bounds.height() > 0)
            canvas.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color);
    }

    /** Scanline-rounded corners work with GuiGraphics without custom shaders or render pipelines. */
    public static void rounded(UiCanvas canvas, Rect bounds, int radius, int color) {
        int r = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
        if (r <= 0) { fill(canvas, bounds, color); return; }
        canvas.fill(bounds.x(), bounds.y() + r, bounds.right(), bounds.bottom() - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double boundary = r - Math.sqrt(r * r - dy * dy);
            int inset = (int) Math.floor(boundary);
            int alpha = (int) Math.round((color >>> 24) * (1 - (boundary - inset)));
            int edge = color & 0x00FFFFFF | alpha << 24;
            roundedScanline(canvas, bounds.x() + inset, bounds.right() - inset,
                    bounds.y() + row, color, edge);
            roundedScanline(canvas, bounds.x() + inset, bounds.right() - inset,
                    bounds.bottom() - row - 1, color, edge);
        }
    }

    private static void roundedScanline(UiCanvas canvas, int left, int right, int y, int color, int edge) {
        canvas.fill(left + 1, y, right - 1, y + 1, color);
        canvas.fill(left, y, left + 1, y + 1, edge);
        canvas.fill(right - 1, y, right, y + 1, edge);
    }

    public static void outlined(UiCanvas canvas, Rect bounds, int radius, int border, int background) {
        rounded(canvas, bounds, radius, border);
        rounded(canvas, bounds.inset(1), Math.max(0, radius - 1), background);
    }

    public static void shadow(UiCanvas canvas, Rect bounds, int radius) {
        rounded(canvas, new Rect(bounds.x() - 3, bounds.y() + 2, bounds.width() + 6, bounds.height() + 3),
                radius + 3, 0x18000000);
        rounded(canvas, new Rect(bounds.x() - 1, bounds.y() + 1, bounds.width() + 2, bounds.height() + 2),
                radius + 1, 0x30000000);
    }

    public static void toggle(UiCanvas canvas, Rect bounds, boolean enabled) {
        rounded(canvas, bounds, bounds.height() / 2, enabled ? 0xFF666666 : 0xFF343434);
        int diameter = bounds.height() - 2;
        int x = enabled ? bounds.right() - diameter - 1 : bounds.x() + 1;
        rounded(canvas, new Rect(x, bounds.y() + 1, diameter, diameter), diameter / 2,
                enabled ? 0xFFF1F1F1 : 0xFF858585);
    }

    public static void chevron(UiCanvas canvas, int x, int y, boolean expanded, int color) {
        if (expanded) {
            canvas.fill(x, y, x + 1, y + 1, color);
            canvas.fill(x + 1, y + 1, x + 2, y + 2, color);
            canvas.fill(x + 2, y + 2, x + 3, y + 3, color);
            canvas.fill(x + 3, y + 1, x + 4, y + 2, color);
            canvas.fill(x + 4, y, x + 5, y + 1, color);
        } else {
            canvas.fill(x + 1, y - 1, x + 2, y, color);
            canvas.fill(x + 2, y, x + 3, y + 1, color);
            canvas.fill(x + 3, y + 1, x + 4, y + 2, color);
            canvas.fill(x + 2, y + 2, x + 3, y + 3, color);
            canvas.fill(x + 1, y + 3, x + 2, y + 4, color);
        }
    }

    public static void icon(UiCanvas canvas, ClickGuiModel.Icon icon, int x, int y) {
        switch (icon) {
            case WORLD -> {
                outlined(canvas, new Rect(x, y, 8, 8), 2, TEXT, 0xFF303030);
                canvas.fill(x + 3, y + 1, x + 5, y + 7, TEXT);
                canvas.fill(x + 1, y + 3, x + 7, y + 5, TEXT);
            }
            case MOVEMENT -> {
                chevron(canvas, x, y + 3, false, TEXT);
                chevron(canvas, x + 4, y + 3, false, TEXT);
            }
            case RENDER -> {
                canvas.fill(x, y, x + 3, y + 3, TEXT);
                canvas.fill(x + 5, y, x + 8, y + 3, TEXT);
                canvas.fill(x, y + 5, x + 3, y + 8, TEXT);
                canvas.fill(x + 5, y + 5, x + 8, y + 8, TEXT);
            }
            case PLAYER -> {
                rounded(canvas, new Rect(x + 2, y, 4, 4), 2, TEXT);
                rounded(canvas, new Rect(x + 1, y + 5, 6, 3), 1, TEXT);
            }
        }
    }

    public static String fit(UiCanvas canvas, String text, int width) {
        if (width <= 0) return "";
        if (canvas.textWidth(text) <= width) return text;
        String ellipsis = "…";
        if (canvas.textWidth(ellipsis) > width) return "";
        int end = text.length();
        while (end > 0 && canvas.textWidth(text.substring(0, end) + ellipsis) > width)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + ellipsis;
    }

    public static void text(UiCanvas canvas, String text, int x, int y, int width, int color) {
        canvas.text(fit(canvas, text, width), x, y, color);
    }

    public static void centered(UiCanvas canvas, String text, Rect bounds, int color) {
        String fitted = fit(canvas, text, bounds.width() - 4);
        canvas.text(fitted, bounds.x() + (bounds.width() - canvas.textWidth(fitted)) / 2,
                bounds.y() + (bounds.height() - 9) / 2, color);
    }
}
