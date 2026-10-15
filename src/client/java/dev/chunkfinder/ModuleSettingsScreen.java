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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Thin Minecraft adapter: existing modules keep ownership of every setting and side effect. */
public final class ModuleSettingsScreen extends MenuScreen {

    public ModuleSettingsScreen(ModuleManager modules) {
        super("Chunk Finder · Modules", null, modules.interfaceSettings());
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
        gui.search(() -> Minecraft.getInstance().setScreen(new ModuleSearchScreen(this,gui,preferences)));
        gui.preferences(() -> Minecraft.getInstance().setScreen(new InterfaceSettingsScreen(this, modules)));
    }

    @Override protected void init() {
        gui.restoreOrder(preferences.panelOrder(),preferences::panelOrder);
        gui.restorePanels(preferences.minimized(), preferences::minimized);
        super.init();
    }

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
            list.add(toggle("horizontal", "Horizontal flight", "Forward/back stay level; Space/Shift remain vertical", freecam::horizontalFlight, () -> freecam.setHorizontalFlight(!freecam.horizontalFlight())));
            list.add(slider("accel", "Acceleration", "Ticks to ramp up; zero keeps immediate controls", freecam::acceleration, 0, 30, freecam::setAcceleration));
            list.add(slider("brake", "Braking", "Ticks to stop; zero stops immediately", freecam::deceleration, 0, 30, freecam::setDeceleration));
            list.add(new Action("preset", "Speed preset", "Cycle 2x / 5x / 10x; V while Freecam is active", () -> freecam.speed() + "x", freecam::nextSpeedPreset));
            list.add(toggle("terrain", "Full terrain", "Disable section occlusion while flying; uses only loaded data and may cost FPS", freecam::fullTerrain,
                    () -> freecam.setFullTerrain(!freecam.fullTerrain())));
            list.add(toggle("swap-strafe", "Swap A/D", "Reverse only lateral movement for your preferred control layout", freecam::swapStrafe,
                    () -> freecam.setSwapStrafe(!freecam.swapStrafe())));
        } else if (module instanceof ChunkFinderModule chunk) {
            list.add(toggle("strict", "Strict base clues", "Requires strong player-like blocks plus a compact mix of evidence; still a heuristic", chunk::strictBaseClues,
                    () -> chunk.setStrictBaseClues(!chunk.strictBaseClues())));
            list.add(toggle("geodes", "Amethyst geodes", "Natural geodes are separate from base clues; off by default", chunk::showGeodes,
                    () -> chunk.setShowGeodes(!chunk.showGeodes())));
            list.add(slider("sensitivity", "Sensitivity", "Higher values accept weaker activity clues",
                    chunk::sensitivity, 1, 10, value -> chunk.adjustSensitivity(value - chunk.sensitivity())));
            list.add(radius(chunk::simulationDistance, value -> chunk.adjustSimulationDistance(value - chunk.simulationDistance())));
            list.add(new Action("colors", "Outline colors", "Separate colors for base clues and natural geodes", null,
                    () -> Minecraft.getInstance().setScreen(new EspColorScreen(this, "Base Finder", chunk.colors(),
                            List.of(new dev.chunkfinder.core.TargetSelection.Entry("chunkfinder:base_clue", "Base clues"),
                                    new dev.chunkfinder.core.TargetSelection.Entry("chunkfinder:geode", "Amethyst geodes")), chunk::automaticColor, java.util.Set.of("chunkfinder:base_clue","chunkfinder:geode")))));
            list.add(new Action("review", "Review base clues", "Why an area was flagged; ignore/restore individual clue areas for this world session", null,
                    () -> Minecraft.getInstance().setScreen(new BaseClueReviewScreen(this,chunk,preferences))));
            list.add(rescan(chunk::requestRescan));
        } else if (module instanceof BlockEspModule esp) {
            if (!(esp instanceof AncientDebrisFinderModule)) {
                list.add(new Action("targets", "Targets", "Select multiple target types; changes are saved with Apply", esp::targetSummary,
                        () -> Minecraft.getInstance().setScreen(esp instanceof ItemEspModule blocks
                                ? new BlockPickerScreen(this, blocks)
                                : new TargetPickerScreen(this, esp.name() + " targets", esp.targetChoices(), esp.targetIds(), esp::setTargetIds))));
            }
            if (esp instanceof AncientDebrisFinderModule)
                list.add(toggle("exposed", "Exposed only", "Reduces buried server replacements; client data cannot verify hidden ore", esp::exposedOnly, () -> esp.setExposedOnly(!esp.exposedOnly())));
            else list.add(toggle("buried", "Buried targets", "On: include non-exposed client-reported blocks. Cannot recover server-hidden real states", () -> !esp.exposedOnly(), () -> esp.setExposedOnly(!esp.exposedOnly())));
            list.add(new Action("rules", "Target rules", "Per-target priority, distance and visible limits", () -> esp.policies().snapshot().targets().size() + " overrides",
                    () -> Minecraft.getInstance().setScreen(new TargetRulesScreen(this,esp.name(),preferences,esp.targetChoices(),esp.policies().snapshot(),esp::setPolicies))));
            list.add(new Action("colors", "ESP colors", "Module color and per-block overrides; does not change target selection", null,
                    () -> Minecraft.getInstance().setScreen(new EspColorScreen(this, esp.name(), esp.colors(), esp.targetChoices(), id -> esp.colors().fallback(), esp.targetIds()))));
            list.add(radius(esp::searchRadius, esp::setSearchRadius));
            list.add(toggle("traces", "Trace lines", "Connect your view to detected blocks", esp::tracesEnabled,
                    () -> esp.setTracesEnabled(!esp.tracesEnabled())));
            list.add(toggle("outlines", "Outlines", "Highlight selected blocks through terrain", esp::blockHighlightEnabled,
                    () -> esp.setBlockHighlightEnabled(!esp.blockHighlightEnabled())));
            list.add(rescan(esp::requestRescan));
        } else if (module instanceof EntityEspModule entities) {
            list.add(new Action("targets", "Targets", "Select entity types or dropped item IDs; empty means no matches", entities::targetSummary,
                    () -> Minecraft.getInstance().setScreen(new TargetPickerScreen(this, entities.name() + " targets",
                            entities.targetChoices(), entities.targetIds(), entities::setTargetIds))));
            list.add(new Action("rules", "Target rules", "Per-type/item priority, distance and visible limits", () -> entities.policies().snapshot().targets().size() + " overrides",
                    () -> Minecraft.getInstance().setScreen(new TargetRulesScreen(this,entities.name(),preferences,entities.targetChoices(),entities.policies().snapshot(),entities::setPolicies))));
            list.add(new Action("colors", "ESP colors", "Module color and per-type/item overrides", null,
                    () -> Minecraft.getInstance().setScreen(new EspColorScreen(this, entities.name(), entities.colors(), entities.targetChoices(), entities::automaticColor, entities.targetIds()))));
            list.add(range(entities::range, 16, 128, entities::setRange));
            boolean living = entities.mode() == EntityEspModule.Mode.ENTITIES;
            list.add(toggle("option", living ? "Players" : "Stack counts",
                    living ? "Master switch: players must also be selected in Targets" : "Show item stack sizes in the HUD",
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
        if (module instanceof ChunkFinderModule) return "Strict, clustered base-like clues · optional natural geodes · not ownership proof";
        if (module instanceof AncientDebrisFinderModule) return "Exposed client-reported debris · server-hidden blocks cannot be verified";
        if (module instanceof ItemEspModule) return "Highlight multiple selected blocks through walls";
        if (module instanceof StorageEspModule) return "Chests, barrels, shulkers and other loaded storage";
        if (module instanceof SpawnerEspModule) return "Mob and trial spawners in loaded chunks";
        if (module instanceof EntityEspModule entities) return entities.mode() == EntityEspModule.Mode.ENTITIES
                ? "Selected entity types sent to your client" : "Dropped-item entities sent to your client";
        if (module instanceof TrajectoriesModule) return "Preview moving projectile and item paths";
        if (module instanceof TargetRendererModule) return "Outline your current crosshair target";
        return "Explore without moving your player · G to toggle Freecam";
    }
}
