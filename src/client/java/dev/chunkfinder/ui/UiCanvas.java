package dev.chunkfinder.ui;

/** Small rendering boundary shared by Minecraft and the headless UI regression tests. */
public interface UiCanvas {
    void fill(int left, int top, int right, int bottom, int color);
    void text(String text, int x, int y, int color);
    int textWidth(String text);
    void pushClip(UiGeometry.Rect bounds);
    void popClip();
}
