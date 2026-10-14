package dev.chunkfinder;

import dev.chunkfinder.core.TargetSelection;
import dev.chunkfinder.core.TargetSelection.Entry;
import dev.chunkfinder.ui.PickerLayout;
import dev.chunkfinder.ui.UiCanvas;
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
import java.util.Set;
import java.util.function.Consumer;

import static dev.chunkfinder.ui.UiTheme.*;

/** Registry-backed multi-select dialog. All edits remain a draft until explicit Apply. */
public class TargetPickerScreen extends Screen {
    private final Screen parent;
    private final String heading;
    private final TargetSelection selection;
    private final Consumer<Set<String>> apply;
    private EditBox search;
    private int page, keyboardIndex = -1;
    private Focus focus = Focus.SEARCH;
    private enum Focus { SEARCH, SELECTED, SELECT_RESULTS, CLEAR_RESULTS, CLEAR_ALL, LIST, PREVIOUS, NEXT, CANCEL, APPLY }

    public TargetPickerScreen(Screen parent, String title, Collection<Entry> choices,
                              Collection<String> initial, Consumer<Set<String>> apply) {
        super(Component.literal(title));
        this.parent = parent; this.heading = title; this.apply = apply;
        selection = new TargetSelection(choices, initial);
    }

    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        int savedPage = page, savedIndex = keyboardIndex;
        Rect field = layout().search();
        // Use native text metrics in EditBox, including its hint: a formatted Inter string
        // with vanilla caret/hit-test widths made search clicks and selections misalign.
        search = new EditBox(font, field.x() + 7, field.y() + 7, Math.max(1, field.width() - 32), 13,
                Component.literal("Search targets by name or ID"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(TEXT); search.setTextColorUneditable(MUTED); search.setTextShadow(false);
        search.setHint(Component.literal("Search name or registry ID"));
        search.setResponder(value -> { selection.query(value); page = 0; keyboardIndex = -1; });
        search.setValue(query);
        List<Entry> entries = selection.filtered();
        keyboardIndex = savedIndex >= 0 && savedIndex < entries.size() ? savedIndex : -1;
        page = keyboardIndex >= 0 ? layout().pageContaining(keyboardIndex, entries.size()) : layout().clampPage(savedPage, entries.size());
        addRenderableWidget(search);
        setInitialFocus(search);
        focus(focus);
    }
    private void focus(Focus value) {
        focus = value;
        if (value != Focus.SEARCH) setDragging(false);
        setFocused(value == Focus.SEARCH ? search : null);
        search.setFocused(value == Focus.SEARCH);
        if (value == Focus.LIST && keyboardIndex < 0 && !selection.filtered().isEmpty()) keyboardIndex = page * layout().pageSize();
    }
    private PickerLayout layout() { return PickerLayout.at(width, height); }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0x58000000);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiCanvas canvas = new MinecraftUiCanvas(graphics, font);
        PickerLayout ui = layout(); Rect panel = ui.panel();
        List<Entry> entries = selection.filtered();
        page = ui.clampPage(page, entries.size());
        shadow(canvas, panel, 10); outlined(canvas, panel, 10, BORDER, PANEL);
        text(canvas, heading, panel.x() + 12, panel.y() + 12, panel.width() - 50, TEXT);
        String status = selection.count() + " selected · " + entries.size() + " results";
        if (selection.count() == 0) status += " · matches nothing";
        else if (selection.unavailableCount() > 0) status += " · " + selection.unavailableCount() + " unavailable";
        if (selection.changed()) status += " · unsaved";
        text(canvas, status, panel.x() + 12, panel.y() + 27, panel.width() - 24, MUTED);
        if (ui.back().contains(mouseX, mouseY)) rounded(canvas, ui.back(), 3, ROW_HOVER);
        centered(canvas, "×", ui.back(), MUTED);
        outlined(canvas, ui.search(), 4, focus == Focus.SEARCH ? FOCUS : BORDER, ROW);
        if (!search.getValue().isEmpty()) centered(canvas, "×", ui.clearSearch(), MUTED);
        button(canvas, ui.selectedOnly(), "Selected", Focus.SELECTED, true, selection.selectedOnly(), mouseX, mouseY);
        button(canvas, ui.selectResults(), "All results", Focus.SELECT_RESULTS, !entries.isEmpty(), false, mouseX, mouseY);
        button(canvas, ui.clearResults(), "Clear results", Focus.CLEAR_RESULTS, !entries.isEmpty(), false, mouseX, mouseY);
        button(canvas, ui.clearAll(), "Clear all", Focus.CLEAR_ALL, selection.count() > 0, false, mouseX, mouseY);
        canvas.pushClip(ui.list());
        int start = page * ui.pageSize();
        for (int row = 0; row < ui.pageSize() && start + row < entries.size(); row++) {
            Entry entry = entries.get(start + row); Rect bounds = ui.row(row);
            boolean selected = selection.selected(entry.id());
            boolean hovered = bounds.contains(mouseX, mouseY) && ui.list().contains(mouseX, mouseY);
            boolean focused = focus == Focus.LIST && keyboardIndex == start + row;
            outlined(canvas, bounds, 4, focused ? FOCUS : selected ? ACTIVE_BORDER : BORDER,
                    hovered ? ROW_HOVER : selected ? ACTIVE : ROW);
            text(canvas, entry.name(), bounds.x() + 8, bounds.y() + 3, bounds.width() - 38, entry.available() ? TEXT : MUTED);
            text(canvas, entry.id(), bounds.x() + 8, bounds.y() + 15, bounds.width() - 38, MUTED);
            toggle(canvas, new Rect(bounds.right() - 24, bounds.y() + 9, 16, 8), selected);
        }
        if (entries.isEmpty()) centered(canvas, selection.selectedOnly() ? "No selected targets match" : "No matching targets", ui.list(), MUTED);
        canvas.popClip();
        button(canvas, ui.previous(), "Previous", Focus.PREVIOUS, page > 0, false, mouseX, mouseY);
        button(canvas, ui.next(), "Next", Focus.NEXT, page + 1 < ui.pageCount(entries.size()), false, mouseX, mouseY);
        centered(canvas, (page + 1) + " / " + ui.pageCount(entries.size()),
                new Rect(ui.previous().right(), ui.previous().y(), ui.next().x() - ui.previous().right(), ui.previous().height()), MUTED);
        button(canvas, ui.cancel(), "Cancel", Focus.CANCEL, true, false, mouseX, mouseY);
        button(canvas, ui.apply(), "Apply (" + selection.count() + ")", Focus.APPLY, true, true, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        PickerLayout ui = layout(); double x = event.x(), y = event.y();
        if (ui.back().contains(x, y)) { onClose(); return true; }
        if (ui.clearSearch().contains(x, y)) { search.setValue(""); focus(Focus.SEARCH); return true; }
        if (ui.search().contains(x, y)) {
            search.mouseClicked(event, doubleClick); focus(Focus.SEARCH); setDragging(true); return true;
        }
        for (Focus value : Focus.values()) {
            Rect button = bounds(ui, value);
            if (button != null && button.contains(x, y)) { focus(value); activate(value); return true; }
        }
        if (ui.list().contains(x, y)) {
            List<Entry> entries = selection.filtered();
            page = ui.clampPage(page, entries.size());
            for (int row = 0; row < ui.pageSize(); row++) {
                int index = page * ui.pageSize() + row;
                if (index < entries.size() && ui.row(row).contains(x, y)) {
                    keyboardIndex = index; focus(Focus.LIST); toggleRow(); return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (layout().list().contains(x, y) && vertical != 0) { page(vertical > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 258) { // Tab / Shift-Tab: all custom buttons and list are keyboard reachable.
            int direction = (event.modifiers() & 1) != 0 ? -1 : 1;
            focus(Focus.values()[Math.floorMod(focus.ordinal() + direction, Focus.values().length)]);
            return true;
        }
        if ((key == 257 || key == 335) && (event.modifiers() & 2) != 0) { activate(Focus.APPLY); return true; }
        if ((focus == Focus.SEARCH || focus == Focus.LIST) && (key == 264 || key == 265)) {
            List<Entry> entries = selection.filtered();
            if (entries.isEmpty()) return true;
            int direction = key == 264 ? 1 : -1;
            if (focus == Focus.SEARCH || keyboardIndex < 0) keyboardIndex = page * layout().pageSize();
            else keyboardIndex = Math.max(0, Math.min(entries.size() - 1, keyboardIndex + direction));
            focus(Focus.LIST); page = layout().pageContaining(keyboardIndex, entries.size()); return true;
        }
        if (focus != Focus.SEARCH && (key == 32 || key == 257 || key == 335)) { activate(focus); return true; }
        if (focus == Focus.LIST && (key == 266 || key == 267)) { page(key == 266 ? -1 : 1); return true; }
        return super.keyPressed(event);
    }

    private void toggleRow() {
        List<Entry> entries = selection.filtered();
        if (keyboardIndex < 0 || keyboardIndex >= entries.size()) return;
        selection.toggle(entries.get(keyboardIndex).id());
        int count = selection.filtered().size();
        keyboardIndex = count == 0 ? -1 : Math.min(keyboardIndex, count - 1);
        page = layout().clampPage(page, count);
    }
    private void page(int direction) {
        page = layout().clampPage(page + direction, selection.filtered().size()); keyboardIndex = -1;
        if (focus == Focus.LIST && !selection.filtered().isEmpty()) keyboardIndex = page * layout().pageSize();
    }
    private void activate(Focus value) {
        switch (value) {
            case SELECTED -> { selection.selectedOnly(!selection.selectedOnly()); page = 0; keyboardIndex = -1; }
            case SELECT_RESULTS -> selection.selectFiltered();
            case CLEAR_RESULTS -> { selection.clearFiltered(); page = layout().clampPage(page, selection.filtered().size()); keyboardIndex = -1; }
            case CLEAR_ALL -> { selection.clearAll(); page = layout().clampPage(page, selection.filtered().size()); keyboardIndex = -1; }
            case LIST -> toggleRow();
            case PREVIOUS -> page(-1);
            case NEXT -> page(1);
            case CANCEL -> onClose();
            case APPLY -> { selection.apply(apply); Minecraft.getInstance().setScreen(parent); }
            default -> { }
        }
    }
    @Override public void onClose() { selection.cancel(); Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static Rect bounds(PickerLayout ui, Focus value) {
        return switch (value) {
            case SELECTED -> ui.selectedOnly(); case SELECT_RESULTS -> ui.selectResults();
            case CLEAR_RESULTS -> ui.clearResults(); case CLEAR_ALL -> ui.clearAll();
            case PREVIOUS -> ui.previous(); case NEXT -> ui.next(); case CANCEL -> ui.cancel(); case APPLY -> ui.apply();
            default -> null;
        };
    }
    private void button(UiCanvas canvas, Rect bounds, String label, Focus value, boolean enabled, boolean active, int x, int y) {
        outlined(canvas, bounds, 4, focus == value ? FOCUS : active ? ACTIVE_BORDER : BORDER,
                enabled && bounds.contains(x, y) ? ROW_HOVER : active ? ACTIVE : ROW);
        centered(canvas, label, bounds, enabled ? TEXT : QUIET);
    }
}
