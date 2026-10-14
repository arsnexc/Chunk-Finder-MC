package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Headless fixture, not Minecraft: exercises the production layout and painter with the bundled font. */
final class AwtUiCanvas implements UiCanvas, AutoCloseable {
    private final BufferedImage image;
    private final Graphics2D graphics;
    private final List<Shape> clips = new ArrayList<>();

    AwtUiCanvas(int width, int height, int scale) throws Exception {
        image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        graphics.scale(scale, scale);
        graphics.setColor(new Color(0x111111));
        graphics.fillRect(0, 0, width, height);
        Font font = Font.createFont(Font.TRUETYPE_FONT, Path.of("src/main/resources/assets/chunkfinder/font/inter.ttf").toFile());
        graphics.setFont(font.deriveFont(9.0f));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    BufferedImage image() { return image; }
    int clipDepth() { return clips.size(); }

    @Override public void fill(int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) return;
        graphics.setColor(new Color(color, true));
        graphics.fillRect(left, top, right - left, bottom - top);
    }
    @Override public void text(String text, int x, int y, int color) {
        graphics.setColor(new Color(color, true));
        graphics.drawString(text, x, y + 8);
    }
    @Override public int textWidth(String text) { return graphics.getFontMetrics().stringWidth(text); }
    @Override public void pushClip(Rect bounds) {
        clips.add(graphics.getClip());
        graphics.clipRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }
    @Override public void popClip() {
        if (clips.isEmpty()) throw new AssertionError("Unbalanced scissor pop");
        graphics.setClip(clips.removeLast());
    }
    @Override public void close() {
        if (!clips.isEmpty()) throw new AssertionError("Leaked scissor bounds");
        graphics.dispose();
    }
}
