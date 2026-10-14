package dev.chunkfinder;

import dev.chunkfinder.ui.UiCanvas;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Minecraft 1.21.11 adapter for the shared, independently testable UI renderer. */
final class MinecraftUiCanvas implements UiCanvas {
    private static final Style UI_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("chunkfinder", "ui")));
    private final GuiGraphics graphics;
    private final Font font;

    MinecraftUiCanvas(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    static Component component(String text) { return Component.literal(text).withStyle(UI_STYLE); }

    @Override public void fill(int left, int top, int right, int bottom, int color) {
        if (right > left && bottom > top) graphics.fill(left, top, right, bottom, color);
    }
    @Override public void text(String text, int x, int y, int color) {
        graphics.drawString(font, component(text), x, y, color, false);
    }
    @Override public int textWidth(String text) { return font.width(component(text)); }
    @Override public void pushClip(Rect bounds) {
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
    }
    @Override public void popClip() { graphics.disableScissor(); }
}
