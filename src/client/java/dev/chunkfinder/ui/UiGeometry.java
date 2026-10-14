package dev.chunkfinder.ui;

/** Screen-space geometry. Hit tests use the same half-open bounds as drawing/scissoring. */
public final class UiGeometry {
    private UiGeometry() { }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public Rect inset(int padding) {
            return new Rect(x + padding, y + padding, width - padding * 2, height - padding * 2);
        }
        public Rect intersect(Rect other) {
            int left = Math.max(x, other.x), top = Math.max(y, other.y);
            return new Rect(left, top, Math.min(right(), other.right()) - left,
                    Math.min(bottom(), other.bottom()) - top);
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    public static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    public static int sliderValue(double mouseX, Rect track, int min, int max) {
        if (track.width <= 1 || max <= min) return min;
        double fraction = clamp((mouseX - track.x) / (track.width - 1), 0, 1);
        return min + (int) Math.round(fraction * (max - min));
    }

    public static int sliderPosition(int value, Rect track, int min, int max) {
        if (track.width <= 1 || max <= min) return track.x;
        double fraction = (clamp(value, min, max) - min) / (double) (max - min);
        return track.x + (int) Math.round(fraction * (track.width - 1));
    }

    public static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    public static Rect verticalThumb(Rect track, int contentHeight, int viewportHeight, double offset) {
        int max = maxScroll(contentHeight, viewportHeight);
        if (max == 0 || track.height == 0) return new Rect(track.x, track.y, track.width, 0);
        int size = Math.min(track.height, Math.max(16,
                (int) Math.round(track.height * viewportHeight / (double) contentHeight)));
        int top = track.y + (int) Math.round((track.height - size) * clamp(offset, 0, max) / max);
        return new Rect(track.x, top, track.width, size);
    }

    public static double scrollFromThumb(double pointer, double grabOffset, Rect track,
                                         int thumbHeight, int maxScroll) {
        int travel = track.height - thumbHeight;
        if (travel <= 0) return 0;
        return clamp((pointer - grabOffset - track.y) / travel, 0, 1) * maxScroll;
    }
}
