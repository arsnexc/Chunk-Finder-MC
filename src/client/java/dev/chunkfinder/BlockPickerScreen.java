package dev.chunkfinder;

import dev.chunkfinder.ui.PickerLayout;
import dev.chunkfinder.ui.UiCanvas;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static dev.chunkfinder.ui.UiTheme.*;

/** Restyled single-target picker. Choosing a row retains the existing immediate-save behavior. */
public final class BlockPickerScreen extends Screen {
    private static final List<BlockEntry> BLOCKS = loadBlocks();
    private final Screen parent;
    private final ItemEspModule module;
    private EditBox search;
    private List<BlockEntry> matches = BLOCKS;
    private int page;
    private int keyboardIndex = -1;

    public BlockPickerScreen(Screen parent, ItemEspModule module) {
        super(Component.literal("Choose a block"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        int savedPage = page, savedIndex = keyboardIndex;
        PickerLayout ui = layout();
        Rect field = ui.search();
        search = new EditBox(font, field.x() + 9, field.y() + 9, Math.max(1, field.width() - 40), 14,
                MinecraftUiCanvas.component("Search blocks"));
        search.setMaxLength(128);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setTextColorUneditable(MUTED);
        search.setHint(MinecraftUiCanvas.component("Search name or minecraft:stone"));
        search.addFormatter((value, cursor) -> MinecraftUiCanvas.component(value).getVisualOrderText());
        search.setResponder(this::filter);
        search.setValue(query);
        keyboardIndex = savedIndex >= 0 && savedIndex < matches.size() ? savedIndex : -1;
        page = keyboardIndex >= 0 ? ui.pageContaining(keyboardIndex, matches.size()) : ui.clampPage(savedPage, matches.size());
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    private void filter(String value) {
        String query = value.trim().toLowerCase(Locale.ROOT);
        matches = query.isEmpty() ? BLOCKS : BLOCKS.stream().filter(entry -> entry.id().contains(query)
                || entry.name().toLowerCase(Locale.ROOT).contains(query)).toList();
        page = 0;
        keyboardIndex = -1;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0x58000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiCanvas canvas = new MinecraftUiCanvas(graphics, font);
        PickerLayout ui = layout();
        Rect panel = ui.panel();
        shadow(canvas, panel, 10);
        outlined(canvas, panel, 10, BORDER, PANEL);
        text(canvas, "Choose a block", panel.x() + 14, panel.y() + 16, panel.width() - 54, TEXT);
        text(canvas, "Selected: " + module.targetBlockName(), panel.x() + 14, panel.y() + 32, panel.width() - 28, MUTED);
        if (ui.back().contains(mouseX, mouseY)) rounded(canvas, ui.back(), 4, ROW_HOVER);
        centered(canvas, "×", ui.back(), MUTED);
        outlined(canvas, ui.search(), 5, search.isFocused() ? ACTIVE_BORDER : BORDER, 0xFF222222);
        if (!search.getValue().isEmpty()) {
            if (ui.clearSearch().contains(mouseX, mouseY)) rounded(canvas, ui.clearSearch(), 3, ROW_HOVER);
            centered(canvas, "×", ui.clearSearch(), MUTED);
        }
        text(canvas, matches.size() + " blocks", ui.list().x(), ui.search().bottom() + 6, ui.list().width(), QUIET);
        page = ui.clampPage(page, matches.size());
        int start = page * ui.pageSize();
        canvas.pushClip(ui.list());
        for (int row = 0; row < ui.pageSize() && start + row < matches.size(); row++) {
            BlockEntry entry = matches.get(start + row);
            Rect bounds = ui.row(row);
            boolean selected = entry.id().equals(module.targetBlockId());
            boolean hovered = bounds.contains(mouseX, mouseY) && ui.list().contains(mouseX, mouseY);
            outlined(canvas, bounds, 5, start + row == keyboardIndex ? FOCUS : selected ? ACTIVE_BORDER : BORDER,
                    hovered ? ROW_HOVER : selected ? ACTIVE : ROW);
            text(canvas, entry.name(), bounds.x() + 9, bounds.y() + 4, bounds.width() - 43, TEXT);
            text(canvas, entry.id(), bounds.x() + 9, bounds.y() + 17, bounds.width() - 43, MUTED);
            toggle(canvas, new Rect(bounds.right() - 25, bounds.y() + 10, 16, 8), selected);
        }
        if (matches.isEmpty()) {
            centered(canvas, "No blocks found", ui.list(), MUTED);
        }
        canvas.popClip();
        button(canvas, ui.previous(), "Previous", page > 0, mouseX, mouseY);
        button(canvas, ui.next(), "Next", page + 1 < ui.pageCount(matches.size()), mouseX, mouseY);
        centered(canvas, (page + 1) + " / " + ui.pageCount(matches.size()),
                new Rect(ui.previous().right(), ui.previous().y(), ui.next().x() - ui.previous().right(), 22), MUTED);
        text(canvas, "Select to save · Esc to go back", panel.x() + 14, panel.bottom() - 13, panel.width() - 28, QUIET);
        // Draw the native EditBox last so its text/caret sit above the custom search-field background.
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        PickerLayout ui = layout();
        double x = event.x(), y = event.y();
        if (ui.back().contains(x, y)) { onClose(); return true; }
        if (ui.clearSearch().contains(x, y)) {
            search.setValue("");
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (ui.search().contains(x, y)) {
            search.mouseClicked(event, doubleClick);
            setFocused(search);
            // Padding belongs to the custom field too, even outside the native text hitbox.
            search.setFocused(true);
            return true;
        }
        page = ui.clampPage(page, matches.size());
        if (ui.list().contains(x, y)) for (int row = 0; row < ui.pageSize(); row++) {
            int index = page * ui.pageSize() + row;
            if (index < matches.size() && ui.row(row).contains(x, y)) { choose(index); return true; }
        }
        if (ui.previous().contains(x, y)) { changePage(-1); return true; }
        if (ui.next().contains(x, y)) { changePage(1); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (layout().list().contains(x, y) && vertical != 0) {
            changePage(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!matches.isEmpty() && (event.key() == 264 || event.key() == 265)) {
            int direction = event.key() == 264 ? 1 : -1;
            keyboardIndex = keyboardIndex < 0 ? page * layout().pageSize()
                    : Math.max(0, Math.min(matches.size() - 1, keyboardIndex + direction));
            page = layout().pageContaining(keyboardIndex, matches.size());
            return true;
        }
        if ((event.key() == 257 || event.key() == 335) && keyboardIndex >= 0 && keyboardIndex < matches.size()) {
            choose(keyboardIndex);
            return true;
        }
        return super.keyPressed(event);
    }

    private void choose(int index) {
        module.setTargetBlock(matches.get(index).id());
        Minecraft.getInstance().setScreen(parent);
    }
    private void changePage(int delta) {
        page = layout().clampPage(page + delta, matches.size());
        keyboardIndex = -1;
    }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private PickerLayout layout() { return PickerLayout.at(width, height); }

    private static void button(UiCanvas canvas, Rect bounds, String label, boolean enabled, int mouseX, int mouseY) {
        outlined(canvas, bounds, 5, enabled ? ACTIVE_BORDER : BORDER,
                enabled && bounds.contains(mouseX, mouseY) ? ROW_HOVER : ROW);
        centered(canvas, label, bounds, enabled ? TEXT : QUIET);
    }

    private static List<BlockEntry> loadBlocks() {
        List<BlockEntry> entries = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR) continue;
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            entries.add(new BlockEntry(id.toString(), block.getName().getString()));
        }
        entries.sort(Comparator.comparing(BlockEntry::id));
        return List.copyOf(entries);
    }
    private record BlockEntry(String id, String name) { }
}
