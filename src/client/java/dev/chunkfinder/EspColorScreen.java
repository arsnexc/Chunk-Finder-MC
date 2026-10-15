package dev.chunkfinder;

import dev.chunkfinder.core.EspPalette;
import dev.chunkfinder.core.TargetSelection.Entry;
import dev.chunkfinder.settings.PaletteConfig;
import dev.chunkfinder.ui.ColorEditorLayout;
import dev.chunkfinder.ui.UiCanvas;
import dev.chunkfinder.ui.UiGeometry;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;
import static dev.chunkfinder.ui.UiTheme.*;

/** Transactional module/per-target RGB editing. Selection and scan state are never modified. */
final class EspColorScreen extends Screen {
    private enum Focus { SCOPE, HEX, RED, GREEN, BLUE, RESET, RESET_ALL, CANCEL, APPLY }
    private final Screen parent;
    private final PaletteConfig config;
    private final EspPalette.Draft draft;
    private final List<Entry> choices;
    private final ToIntFunction<String> automatic;
    private EditBox hex;
    private Focus focus = Focus.HEX, drag;
    private String scope = "";
    private int fallback;
    private boolean syncing, valid = true;

    EspColorScreen(Screen parent, String label, PaletteConfig config, Collection<Entry> choices, ToIntFunction<String> automatic) {
        super(Component.literal(label + " · Colors"));
        this.parent = parent; this.config = config; this.choices = List.copyOf(choices); this.automatic = automatic;
        draft = new EspPalette.Draft(config.snapshot()); fallback = automatic.applyAsInt("");
    }
    private ColorEditorLayout layout() { return ColorEditorLayout.at(width, height); }
    private int color() { return draft.color(scope, fallback); }
    private String scopeName() {
        if (scope.isEmpty()) return "Module default";
        return choices.stream().filter(e -> e.id().equals(scope)).map(Entry::name).findFirst().orElse(scope);
    }
    @Override protected void init() {
        String text = hex == null ? EspPalette.hex(color()) : hex.getValue();
        Rect field = layout().hex();
        hex = new EditBox(font, field.x() + 5, field.y() + 5, field.width() - 10, 12, Component.literal("RGB hexadecimal color"));
        hex.setMaxLength(16); hex.setBordered(false); hex.setTextColor(TEXT); hex.setTextShadow(false);
        hex.setHint(Component.literal("#RRGGBB"));
        hex.setResponder(value -> {
            if (syncing) return;
            var parsed = EspPalette.parse(value); valid = parsed.isPresent();
            if (valid) draft.set(scope, parsed.getAsInt());
        });
        syncing = true; hex.setValue(text); syncing = false; valid = EspPalette.parse(text).isPresent();
        addRenderableWidget(hex); setInitialFocus(hex); focus(focus); drag = null;
    }
    private void focus(Focus value) { focus = value; setFocused(value == Focus.HEX ? hex : null); if (hex != null) hex.setFocused(value == Focus.HEX); }
    private void sync() {
        if (hex == null) return;
        syncing = true; hex.setValue(EspPalette.hex(color())); syncing = false; valid = true;
    }
    @Override public Component getNarrationMessage() {
        return Component.literal(getTitle().getString() + ". " + scopeName() + ". " + EspPalette.hex(color()) + ". " + focus.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {
        super.renderBackground(graphics, x, y, delta); graphics.fill(0, 0, width, height, 0x58000000);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        UiCanvas canvas = new MinecraftUiCanvas(graphics, font); ColorEditorLayout ui = layout(); Rect panel = ui.panel();
        shadow(canvas, panel, 9); outlined(canvas, panel, 9, BORDER, PANEL);
        text(canvas, getTitle().getString(), panel.x() + 12, panel.y() + 12, panel.width() - 24, TEXT);
        text(canvas, !valid ? "Enter exactly six hex digits · #RRGGBB" : draft.changed() ? "Unsaved colors · Apply to keep" : "Colors only · target selection stays unchanged",
                panel.x() + 12, panel.y() + 26, panel.width() - 24, valid ? MUTED : 0xFFFF9090);
        button(canvas, ui.scope(), scopeName() + "  >", Focus.SCOPE, true, x, y);
        outlined(canvas, ui.hex(), 4, focus == Focus.HEX ? FOCUS : BORDER, ROW);
        outlined(canvas, ui.preview(), 4, TEXT, color());
        slider(canvas, ui.red(), Focus.RED, "R", 16, x, y);
        slider(canvas, ui.green(), Focus.GREEN, "G", 8, x, y);
        slider(canvas, ui.blue(), Focus.BLUE, "B", 0, x, y);
        button(canvas, ui.reset(), scope.isEmpty() ? "Use defaults" : "Use module color", Focus.RESET, true, x, y);
        button(canvas, ui.resetAll(), "Reset all colors", Focus.RESET_ALL, true, x, y);
        button(canvas, ui.cancel(), "Cancel", Focus.CANCEL, true, x, y);
        button(canvas, ui.apply(), "Apply colors", Focus.APPLY, valid, x, y);
        super.render(graphics, x, y, delta);
    }
    private void slider(UiCanvas canvas, Rect track, Focus value, String label, int shift, int x, int y) {
        int current = color(), channel = current >>> shift & 255;
        if (focus == value) outlined(canvas, ColorEditorLayout.sliderHit(track), 3, FOCUS, ROW);
        text(canvas, label + " " + channel, track.x() - 26, track.y() - 10, track.width() + 20, TEXT);
        for (int px = 0; px < track.width(); px++) {
            int amount = UiGeometry.sliderValue(track.x() + px, track, 0, 255);
            int tint = current & ~(255 << shift) | amount << shift;
            canvas.fill(track.x() + px, track.y(), track.x() + px + 1, track.bottom(), tint);
        }
        int thumb = UiGeometry.sliderPosition(channel, track, 0, 255);
        rounded(canvas, new Rect(thumb - 2, track.y() - 2, 5, 7), 2, TEXT);
    }
    private void button(UiCanvas canvas, Rect rect, String label, Focus value, boolean enabled, int x, int y) {
        outlined(canvas, rect, 4, focus == value ? FOCUS : BORDER, enabled && rect.contains(x, y) ? ROW_HOVER : ROW);
        centered(canvas, label, rect, enabled ? TEXT : QUIET);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        ColorEditorLayout ui = layout(); double x = event.x(), y = event.y();
        if (ui.hex().contains(x, y)) { focus(Focus.HEX); hex.mouseClicked(event, doubleClick); setDragging(true); return true; }
        for (Focus value : Focus.values()) {
            Rect bounds = bounds(ui, value);
            if (bounds != null && bounds.contains(x, y)) {
                focus(value);
                if (channel(value) >= 0) { drag = value; changeChannel(value, UiGeometry.sliderValue(x, track(ui, value), 0, 255)); }
                else activate(value);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && drag != null) { changeChannel(drag, UiGeometry.sliderValue(event.x(), track(layout(), drag), 0, 255)); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && drag != null) { drag = null; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 258) { drag = null; focus(Focus.values()[Math.floorMod(focus.ordinal() + ((event.modifiers() & 1) == 0 ? 1 : -1), Focus.values().length)]); triggerImmediateNarration(false); return true; }
        if ((key == 257 || key == 335) && (event.modifiers() & 2) != 0) { activate(Focus.APPLY); return true; }
        if (channel(focus) >= 0 && (key == 262 || key == 263 || key == 268 || key == 269)) {
            int old = color() >>> channel(focus) & 255;
            changeChannel(focus, key == 268 ? 0 : key == 269 ? 255 : old + (key == 262 ? 1 : -1)); triggerImmediateNarration(false); return true;
        }
        if ((key == 257 || key == 335 || key == 32) && focus != Focus.HEX) { activate(focus); return true; }
        return super.keyPressed(event);
    }
    private void changeChannel(Focus value, int amount) {
        int shift = channel(value), c = color() & ~(255 << shift) | Math.max(0, Math.min(255, amount)) << shift;
        draft.set(scope, c); sync();
    }
    private void activate(Focus value) {
        switch (value) {
            case SCOPE -> {
                if (!valid) { focus(Focus.HEX); return; }
                focus(Focus.SCOPE);
                Minecraft.getInstance().setScreen(TargetPickerScreen.colorChoice(this, choices, draft.snapshot().targets().keySet(), scope, id -> {
                    scope = id; fallback = automatic.applyAsInt(id); focus = Focus.SCOPE; sync(); Minecraft.getInstance().setScreen(this);
                }));
            }
            case RESET -> { draft.reset(scope); sync(); }
            case RESET_ALL -> { draft.resetAll(); sync(); }
            case CANCEL -> onClose();
            case APPLY -> { if (!valid) { focus(Focus.HEX); return; } config.apply(draft.snapshot()); Minecraft.getInstance().setScreen(parent); }
            default -> { }
        }
    }
    private static int channel(Focus focus) { return switch (focus) { case RED -> 16; case GREEN -> 8; case BLUE -> 0; default -> -1; }; }
    private static Rect track(ColorEditorLayout ui, Focus focus) { return switch (focus) { case RED -> ui.red(); case GREEN -> ui.green(); case BLUE -> ui.blue(); default -> null; }; }
    private static Rect bounds(ColorEditorLayout ui, Focus focus) {
        return switch (focus) {
            case SCOPE -> ui.scope(); case HEX -> null;
            case RED, GREEN, BLUE -> ColorEditorLayout.sliderHit(track(ui, focus));
            case RESET -> ui.reset(); case RESET_ALL -> ui.resetAll(); case CANCEL -> ui.cancel(); case APPLY -> ui.apply();
        };
    }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
