package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;

/** Shared geometry for the existing single-block picker, including small GUI scales. */
public record PickerLayout(Rect panel, Rect search, Rect list, Rect previous, Rect next, Rect back,
                           Rect clearSearch, int pageSize) {
    public static final int ROW_HEIGHT = 32;

    public static PickerLayout at(int screenWidth, int screenHeight) {
        int width = Math.max(0, Math.min(430, screenWidth - 28));
        int height = Math.max(0, Math.min(402, screenHeight - 28));
        Rect panel = new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        Rect search = new Rect(panel.x() + 14, panel.y() + 51, width - 28, 27);
        int footer = panel.bottom() - 40;
        Rect list = new Rect(panel.x() + 14, panel.y() + 95, width - 28, footer - 8 - (panel.y() + 95));
        return new PickerLayout(panel, search, list,
                new Rect(panel.x() + 14, footer, 54, 22),
                new Rect(panel.right() - 68, footer, 54, 22),
                new Rect(panel.right() - 30, panel.y() + 13, 16, 16),
                new Rect(search.right() - 24, search.y() + 5, 16, 16),
                Math.max(1, Math.min(8, list.height() / ROW_HEIGHT)));
    }

    public Rect row(int index) {
        return new Rect(list.x(), list.y() + index * ROW_HEIGHT, list.width(), ROW_HEIGHT - 3);
    }

    public int pageCount(int entries) { return Math.max(1, (entries + pageSize - 1) / pageSize); }
    public int clampPage(int page, int entries) { return UiGeometry.clamp(page, 0, pageCount(entries) - 1); }
    public int pageContaining(int itemIndex, int entries) { return clampPage(itemIndex / pageSize, entries); }
}
