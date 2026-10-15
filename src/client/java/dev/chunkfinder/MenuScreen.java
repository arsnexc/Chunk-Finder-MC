package dev.chunkfinder;

import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.UiMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Scaled/narrated adapter shared by module and accessibility menus. */
abstract class MenuScreen extends Screen {
    protected ClickGui gui;
    protected final InterfaceConfig preferences;
    private final Screen parent;
    MenuScreen(String title, Screen parent, InterfaceConfig preferences) {
        super(Component.literal(title)); this.parent = parent; this.preferences = preferences;
    }
    protected double scale() { return UiMotion.fittedScale(preferences.menuScale(), width, height); }
    private void layout() {
        double s = scale(); gui.resize((int) (width / s), (int) (height / s));
        gui.presentation(preferences.reducedMotion(), preferences.contrast(), preferences.hints());
    }
    @Override protected void init() { layout(); }
    @Override public Component getNarrationMessage() {
        return Component.literal(getTitle().getString() + ". " + (gui == null ? "" : gui.focusedLabel()));
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {
        super.renderBackground(graphics, x, y, delta); graphics.fill(0, 0, width, height, 0x58000000);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        layout(); double s = scale();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale((float) s, (float) s);
            gui.render(new MinecraftUiCanvas(graphics, font), (int) (x / s), (int) (y / s));
        } finally { graphics.pose().popMatrix(); }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        layout(); double s = scale();
        boolean used = gui.mouseClicked(event.x() / s, event.y() / s, event.button());
        if (used && Minecraft.getInstance().screen == this) triggerImmediateNarration(false);
        return used || super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return gui.mouseDragged(event.x() / scale(), event.y() / scale(), event.button()) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return gui.mouseReleased(event.button()) || super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return gui.mouseScrolled(x / scale(), y / scale(), horizontal, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        boolean used = gui.keyPressed(event.key(), (event.modifiers() & 1) != 0);
        if (used && Minecraft.getInstance().screen == this) triggerImmediateNarration(false);
        return used || super.keyPressed(event);
    }
    @Override public void removed() { gui.cancelDrag(); super.removed(); }
    @Override public void onClose() { gui.cancelDrag(); Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
