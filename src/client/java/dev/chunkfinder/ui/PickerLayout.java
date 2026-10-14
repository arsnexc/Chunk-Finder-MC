package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;

/** Shared multi-target picker geometry; search, bulk actions, list, paging and Apply/Cancel do not overlap. */
public record PickerLayout(Rect panel, Rect search, Rect list, Rect previous, Rect next, Rect back,
                           Rect clearSearch, Rect selectedOnly, Rect selectResults, Rect clearResults,
                           Rect clearAll, Rect cancel, Rect apply, int pageSize) {
    public static final int ROW_HEIGHT = 28;

    public static PickerLayout at(int screenWidth, int screenHeight) {
        int width = Math.max(0, Math.min(510, screenWidth - 24));
        int height = Math.max(0, Math.min(450, screenHeight - 24));
        Rect panel = new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        Rect search = new Rect(panel.x() + 12, panel.y() + 42, width - 24, 22);
        int toolbarY = panel.y() + 69, toolbarWidth = (width - 24 - 12) / 4;
        int pagingY = panel.bottom() - 56;
        Rect list = new Rect(panel.x() + 12, panel.y() + 93, width - 24, pagingY - 5 - (panel.y() + 93));
        int actionWidth = (width - 30) / 2;
        return new PickerLayout(panel, search, list,
                new Rect(panel.x() + 12, pagingY, 58, 19), new Rect(panel.right() - 70, pagingY, 58, 19),
                new Rect(panel.right() - 28, panel.y() + 10, 16, 16),
                new Rect(search.right() - 22, search.y() + 3, 16, 16),
                new Rect(search.x(), toolbarY, toolbarWidth, 18),
                new Rect(search.x() + toolbarWidth + 4, toolbarY, toolbarWidth, 18),
                new Rect(search.x() + (toolbarWidth + 4) * 2, toolbarY, toolbarWidth, 18),
                new Rect(search.x() + (toolbarWidth + 4) * 3, toolbarY, toolbarWidth, 18),
                new Rect(panel.x() + 12, panel.bottom() - 30, actionWidth, 22),
                new Rect(panel.x() + 18 + actionWidth, panel.bottom() - 30, actionWidth, 22),
                Math.max(1, list.height() / ROW_HEIGHT));
    }
    public Rect row(int index) { return new Rect(list.x(), list.y() + index * ROW_HEIGHT, list.width(), ROW_HEIGHT - 2); }
    public int pageCount(int entries) { return Math.max(1, (entries + pageSize - 1) / pageSize); }
    public int clampPage(int page, int entries) { return UiGeometry.clamp(page, 0, pageCount(entries) - 1); }
    public int pageContaining(int itemIndex, int entries) { return clampPage(itemIndex / pageSize, entries); }
}
