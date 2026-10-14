package dev.chunkfinder;

import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.Action;
import dev.chunkfinder.ui.ClickGuiModel.Category;
import dev.chunkfinder.ui.ClickGuiModel.Entry;
import dev.chunkfinder.ui.ClickGuiModel.Icon;
import dev.chunkfinder.ui.ClickGuiModel.Setting;
import dev.chunkfinder.ui.ClickGuiModel.Slider;
import dev.chunkfinder.ui.ClickGuiModel.Toggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Thin Minecraft adapter: existing modules keep ownership of every setting and side effect. */
public final class ModuleSettingsScreen extends Screen {
    private final ClickGui gui;

    public ModuleSettingsScreen(ModuleManager modules) {
        super(Component.literal("Chunk Finder · Modules"));
        gui = new ClickGui(List.of(
                new Category("world", "World", Icon.WORLD, List.of(
                        entry("chunks", modules.chunkFinder()), entry("debris", modules.ancientDebrisFinder()))),
                new Category("movement", "Movement", Icon.MOVEMENT, List.of(entry("freecam", modules.freecam()))),
                new Category("render", "Render", Icon.RENDER, List.of(
                        entry("blocks", modules.itemEsp()), entry("storage", modules.storageEsp()),
                        entry("spawners", modules.spawnerEsp()), entry("entities", modules.entityEsp()),
                        entry("items", modules.droppedItemEsp()))),
                new Category("player", "Player", Icon.PLAYER, List.of(
                        entry("target", modules.targetRenderer()), entry("trajectories", modules.trajectories())))
        ), this::onClose);
    }

    @Override protected void init() { gui.resize(width, height); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Vanilla owns the blur pass and respects the configured menu-blur strength.
        super.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0x58000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        gui.resize(width, height);
        gui.render(new MinecraftUiCanvas(graphics, font), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return gui.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return gui.mouseDragged(event.x(), event.y(), event.button()) || super.mouseDragged(event, deltaX, deltaY);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return gui.mouseReleased(event.button()) || super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return gui.mouseScrolled(x, y, horizontal, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        return gui.keyPressed(event.key(), (event.modifiers() & 1) != 0) || super.keyPressed(event);
    }
    @Override public void removed() { gui.cancelDrag(); super.removed(); }
    @Override public void onClose() { gui.cancelDrag(); Minecraft.getInstance().setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }

    private Entry entry(String id, Module module) {
        Runnable toggle = () -> {
            if (module instanceof FreecamModule freecam) {
                if (!freecam.toggle(Minecraft.getInstance())) gui.notice("Freecam needs a loaded world and player.");
            } else module.setEnabled(!module.isEnabled());
        };
        return new Entry(id, module.name(), description(module), module::isEnabled, toggle, settings(module));
    }

    private List<Setting> settings(Module module) {
        List<Setting> list = new ArrayList<>();
        if (module instanceof FreecamModule freecam) {
            list.add(slider("speed", "Speed", "Camera movement speed · Hold Sprint to move faster",
                    freecam::speed, 1, 10, freecam::setSpeed));
        } else if (module instanceof ChunkFinderModule chunk) {
            list.add(slider("sensitivity", "Sensitivity", "Higher values accept weaker activity clues",
                    chunk::sensitivity, 1, 10, value -> chunk.adjustSensitivity(value - chunk.sensitivity())));
            list.add(radius(chunk::simulationDistance, value -> chunk.adjustSimulationDistance(value - chunk.simulationDistance())));
        } else if (module instanceof AncientDebrisFinderModule debris) {
            list.add(radius(debris::searchRadius, debris::setSearchRadius));
            list.add(toggle("traces", "Trace lines", "Connect your view to each detected block", debris::tracesEnabled,
                    () -> debris.setTracesEnabled(!debris.tracesEnabled())));
            list.add(toggle("outlines", "Outlines", "Highlight debris through terrain", debris::blockHighlightEnabled,
                    () -> debris.setBlockHighlightEnabled(!debris.blockHighlightEnabled())));
            list.add(rescan(debris::requestRescan));
        } else if (module instanceof ItemEspModule esp) {
            list.add(new Action("block", "Block", "Choose a registered block by name or ID", esp::targetBlockName,
                    () -> Minecraft.getInstance().setScreen(new BlockPickerScreen(this, esp))));
            list.add(radius(esp::searchRadius, esp::setSearchRadius));
            list.add(toggle("traces", "Trace lines", "Connect your view to detected blocks", esp::tracesEnabled,
                    () -> esp.setTracesEnabled(!esp.tracesEnabled())));
            list.add(toggle("outlines", "Outlines", "Highlight blocks through terrain", esp::blockHighlightEnabled,
                    () -> esp.setBlockHighlightEnabled(!esp.blockHighlightEnabled())));
            list.add(rescan(esp::requestRescan));
        } else if (module instanceof StorageEspModule storage) {
            list.add(radius(storage::searchRadius, storage::setSearchRadius));
            list.add(toggle("traces", "Trace lines", "Connect your view to storage blocks", storage::tracesEnabled,
                    () -> storage.setTracesEnabled(!storage.tracesEnabled())));
            list.add(toggle("outlines", "Outlines", "Highlight storage through terrain", storage::blockHighlightEnabled,
                    () -> storage.setBlockHighlightEnabled(!storage.blockHighlightEnabled())));
            list.add(rescan(storage::requestRescan));
        } else if (module instanceof SpawnerEspModule spawners) {
            list.add(radius(spawners::searchRadius, spawners::setSearchRadius));
            list.add(toggle("traces", "Trace lines", "Connect your view to mob and trial spawners", spawners::tracesEnabled,
                    () -> spawners.setTracesEnabled(!spawners.tracesEnabled())));
            list.add(toggle("outlines", "Outlines", "Highlight spawners through terrain", spawners::blockHighlightEnabled,
                    () -> spawners.setBlockHighlightEnabled(!spawners.blockHighlightEnabled())));
            list.add(rescan(spawners::requestRescan));
        } else if (module instanceof EntityEspModule entities) {
            list.add(range(entities::range, 16, 128, entities::setRange));
            boolean living = entities.mode() == EntityEspModule.Mode.LIVING;
            list.add(toggle("option", living ? "Players" : "Stack counts",
                    living ? "Include players as well as mobs" : "Show item stack sizes in the HUD",
                    entities::option, () -> entities.setOption(!entities.option())));
        } else if (module instanceof TrajectoriesModule trajectories) {
            list.add(slider("steps", "Prediction steps", "Approximate flight prediction; not a server outcome guarantee",
                    trajectories::steps, 12, 72, trajectories::setSteps));
            list.add(toggle("items", "Dropped items", "Include moving dropped-item entities", trajectories::showItems,
                    () -> trajectories.setShowItems(!trajectories.showItems())));
        } else if (module instanceof TargetRendererModule target) {
            list.add(range(target::range, 8, 64, target::setRange));
            list.add(toggle("blocks", "Blocks", "Highlight crosshair-selected blocks; entities are always highlighted",
                    target::showBlocks, () -> target.setShowBlocks(!target.showBlocks())));
        }
        return list;
    }

    private static Slider slider(String id, String label, String hint, IntSupplier value, int min, int max, IntConsumer update) {
        return new Slider(id, label, hint, value, min, max, update, Integer::toString);
    }
    private static Slider radius(IntSupplier value, IntConsumer update) {
        return new Slider("radius", "Search radius", "Nearby loaded chunks only; does not load new chunks",
                value, 2, 12, update, n -> n + " ch");
    }
    private static Slider range(IntSupplier value, int min, int max, IntConsumer update) {
        return new Slider("range", "Max distance", "Only client-visible targets within the configured distance",
                value, min, max, update, n -> n + " m");
    }
    private static Toggle toggle(String id, String label, String hint, BooleanSupplier value, Runnable update) {
        return new Toggle(id, label, hint, value, update);
    }
    private static Action rescan(Runnable run) {
        return new Action("rescan", "Rescan chunks", "Refresh blocks in previously scanned, loaded chunks", null, run);
    }
    private static String description(Module module) {
        if (module instanceof ChunkFinderModule) return "Geodes and possible activity clues in loaded chunks";
        if (module instanceof AncientDebrisFinderModule) return "Locate ancient debris in loaded Nether chunks";
        if (module instanceof ItemEspModule) return "Highlight a chosen block through walls";
        if (module instanceof StorageEspModule) return "Chests, barrels, shulkers and other loaded storage";
        if (module instanceof SpawnerEspModule) return "Mob and trial spawners in loaded chunks";
        if (module instanceof EntityEspModule entities) return entities.mode() == EntityEspModule.Mode.LIVING
                ? "Players and mobs sent to your client" : "Dropped-item entities sent to your client";
        if (module instanceof TrajectoriesModule) return "Preview moving projectile and item paths";
        if (module instanceof TargetRendererModule) return "Outline your current crosshair target";
        return "Explore without moving your player · G to toggle Freecam";
    }
}
