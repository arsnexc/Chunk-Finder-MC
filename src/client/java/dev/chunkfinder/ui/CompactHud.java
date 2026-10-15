package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;
import java.util.List;

/** One bounded, text-first HUD column. No panel backgrounds unless explicitly enabled. */
public final class CompactHud {
    public record Row(String label, String value, String detail, int color) { }
    private CompactHud() { }

    public static Rect draw(UiCanvas canvas, int width, int height, List<Row> rows, int maxRows, boolean details, boolean background) {
        int lineHeight = details ? 23 : 12;
        int capacity = Math.max(0, Math.min(maxRows, (height - 24) / lineHeight));
        if (rows.isEmpty() || capacity == 0) return new Rect(8, 8, 0, 0);
        boolean overflow = rows.size() > capacity;
        int count = Math.min(rows.size(), capacity - (overflow ? 1 : 0));
        int maximum = Math.max(1, Math.min(220, width - 16)), wanted = 80;
        for (int i = 0; i < count; i++) {
            Row row = rows.get(i);
            wanted = Math.max(wanted, canvas.textWidth(row.label()) + canvas.textWidth(row.value()) + 25);
            if (details) wanted = Math.max(wanted, canvas.textWidth(row.detail()) + 16);
        }
        int boxWidth = Math.min(maximum, wanted), boxHeight = (count + (overflow ? 1 : 0)) * lineHeight + 8;
        Rect box = new Rect(8, 8, boxWidth, boxHeight);
        if (background) UiTheme.rounded(canvas, box, 5, 0xB8101010);
        canvas.pushClip(new Rect(0, 0, width, height));
        for (int i = 0; i < count; i++) {
            Row row = rows.get(i); int y = 12 + i * lineHeight;
            canvas.fill(10, y + 3, 12, y + 6, row.color());
            int valueWidth = Math.min(boxWidth / 2, canvas.textWidth(row.value()));
            text(canvas, row.label(), 16, y, boxWidth - valueWidth - 19, 0xFFF2F2F2);
            text(canvas, row.value(), box.right() - valueWidth - 4, y, valueWidth, row.color());
            if (details) text(canvas, row.detail(), 16, y + 11, boxWidth - 12, 0xFFC5C5C5);
        }
        if (overflow) text(canvas, "+" + (rows.size() - count) + " more · K", 16, 12 + count * lineHeight, boxWidth - 12, 0xFFC5C5C5);
        canvas.popClip();
        return box;
    }
    private static void text(UiCanvas canvas, String value, int x, int y, int width, int color) {
        String fitted = UiTheme.fit(canvas, value, width);
        canvas.text(fitted, x + 1, y + 1, 0xC0000000);
        canvas.text(fitted, x, y, color);
    }
}
