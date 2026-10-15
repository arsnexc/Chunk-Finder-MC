package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;

/** Fixed, shared RGB/dialog hitboxes with no animation-driven geometry changes. */
public record ColorEditorLayout(Rect panel, Rect scope, Rect hex, Rect preview, Rect red, Rect green, Rect blue,
                                Rect reset, Rect resetAll, Rect cancel, Rect apply, Rect tools) {
    public static ColorEditorLayout at(int width, int height) {
        int w = Math.max(160, Math.min(460, width - 24)), h = Math.max(210, Math.min(256, height - 24));
        int x = (width - w) / 2, y = (height - h) / 2, button = (w - 30) / 2;
        return new ColorEditorLayout(new Rect(x, y, w, h), new Rect(x + 12, y + 40, w - 24, 22),
                new Rect(x + 12, y + 75, w - 84, 18), new Rect(x + w - 56, y + 72, 40, 24),
                new Rect(x + 38, y + 105, w - 52, 3), new Rect(x + 38, y + 128, w - 52, 3),
                new Rect(x + 38, y + 151, w - 52, 3),
                new Rect(x + 12, y + h - 54, button, 18), new Rect(x + 18 + button, y + h - 54, button, 18),
                new Rect(x + 12, y + h - 30, button, 22), new Rect(x + 18 + button, y + h - 30, button, 22),new Rect(x+w-78,y+8,66,16));
    }
    public static Rect sliderHit(Rect track) { return new Rect(track.x() - 3, track.y() - 5, track.width() + 6, 13); }
}
