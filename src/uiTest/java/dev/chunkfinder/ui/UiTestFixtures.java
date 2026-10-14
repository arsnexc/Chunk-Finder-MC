package dev.chunkfinder.ui;

import dev.chunkfinder.ui.ClickGuiModel.Action;
import dev.chunkfinder.ui.ClickGuiModel.Category;
import dev.chunkfinder.ui.ClickGuiModel.Entry;
import dev.chunkfinder.ui.ClickGuiModel.Icon;
import dev.chunkfinder.ui.ClickGuiModel.Setting;
import dev.chunkfinder.ui.ClickGuiModel.Slider;
import dev.chunkfinder.ui.ClickGuiModel.Toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class UiTestFixtures {
    private UiTestFixtures() { }

    static final class Value {
        boolean enabled;
        boolean option = true;
        int number = 5;
        int writes;
        int toggles;
        int actions;
        void toggle() { enabled = !enabled; toggles++; }
        void set(int value) { number = value; writes++; }
    }

    record Fixture(ClickGui gui, List<Value> values, AtomicInteger closed) { }

    static Fixture large() {
        List<Category> categories = new ArrayList<>();
        List<Value> values = new ArrayList<>();
        Icon[] icons = Icon.values();
        for (int column = 0; column < 4; column++) {
            List<Entry> entries = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                Value value = new Value();
                values.add(value);
                entries.add(entry("entry-" + column + "-" + index, "Module " + index, value, List.of(
                        new Slider("number", "Radius", "Adjust the radius", () -> value.number, 1, 10, value::set, Integer::toString),
                        new Toggle("option", "Outlines", "Toggle outlines", () -> value.option, () -> value.option = !value.option),
                        new Action("action", "Rescan", "Rescan loaded chunks", null, () -> value.actions++))));
            }
            categories.add(new Category("category-" + column, "Category " + column, icons[column], entries));
        }
        AtomicInteger closed = new AtomicInteger();
        return new Fixture(new ClickGui(categories, closed::incrementAndGet), values, closed);
    }

    static ClickGui preview() {
        Value chunks = new Value(), debris = new Value(), freecam = new Value(), blocks = new Value();
        Value storage = new Value(), spawners = new Value(), entities = new Value(), items = new Value();
        Value target = new Value(), trajectories = new Value();
        debris.enabled = true;
        freecam.enabled = true;
        blocks.enabled = true;
        spawners.enabled = true;
        entities.enabled = true;
        target.enabled = true;
        chunks.number = 5;
        freecam.number = 4;
        blocks.number = 6;
        target.number = 32;
        return new ClickGui(List.of(
                new Category("world", "World", Icon.WORLD, List.of(
                        entry("chunks", "Chunk Finder", chunks, List.of(
                                slider("sensitivity", "Sensitivity", chunks, 1, 10),
                                new Slider("radius", "Search radius", "Loaded chunks only", () -> 6, 2, 12, n -> { }, n -> n + " ch"))),
                        entry("debris", "Ancient Debris Finder", debris, scanSettings(debris)))),
                new Category("movement", "Movement", Icon.MOVEMENT, List.of(
                        entry("freecam", "Freecam", freecam, List.of(slider("speed", "Speed", freecam, 1, 10))))),
                new Category("render", "Render", Icon.RENDER, List.of(
                        entry("blocks", "Block ESP", blocks, List.of(
                                new Action("targets", "Targets", "Choose several registered blocks", () -> "3 selected", () -> { }),
                                new Slider("radius", "Search radius", "Loaded chunks only", () -> blocks.number, 2, 12, blocks::set, n -> n + " ch"),
                                new Toggle("traces", "Trace lines", "Draw traces", () -> false, () -> { }),
                                new Toggle("outlines", "Outlines", "Draw outlines", () -> true, () -> { }),
                                new Action("rescan", "Rescan chunks", "Refresh loaded chunks", null, () -> { }))),
                        entry("storage", "Storage ESP", storage, scanSettings(storage)),
                        entry("spawners", "Spawner ESP", spawners, scanSettings(spawners)),
                        entry("entities", "Entity ESP", entities, List.of(
                                new Slider("distance", "Max distance", "Visible entities", () -> 64, 16, 128, n -> { }, n -> n + " m"),
                                new Toggle("players", "Players", "Include players", () -> true, () -> { }))),
                        entry("items", "Dropped Item ESP", items, List.of(
                                slider("distance", "Max distance", items, 16, 128),
                                new Toggle("counts", "Stack counts", "Show stack counts", () -> true, () -> { }))))),
                new Category("player", "Player", Icon.PLAYER, List.of(
                        entry("target", "Target Renderer", target, List.of(
                                new Slider("distance", "Max distance", "Crosshair target", () -> target.number, 8, 64, target::set, n -> n + " m"),
                                new Toggle("blocks", "Blocks", "Highlight blocks", () -> true, () -> { }))),
                        entry("trajectories", "Trajectories", trajectories, List.of(
                                slider("steps", "Prediction steps", trajectories, 12, 72),
                                new Toggle("items", "Dropped items", "Include item entities", () -> true, () -> { })))))
        ), () -> { });
    }

    private static List<Setting> scanSettings(Value value) {
        return List.of(new Slider("radius", "Search radius", "Loaded chunks only", () -> value.number, 2, 12, value::set, n -> n + " ch"),
                new Toggle("traces", "Trace lines", "Draw traces", () -> true, () -> { }),
                new Toggle("outlines", "Outlines", "Draw outlines", () -> true, () -> { }),
                new Action("rescan", "Rescan chunks", "Refresh loaded chunks", null, () -> { }));
    }
    private static Slider slider(String id, String label, Value value, int min, int max) {
        return new Slider(id, label, label, () -> value.number, min, max, value::set, Integer::toString);
    }
    private static Entry entry(String id, String name, Value value, List<Setting> settings) {
        return new Entry(id, name, name + " · Test fixture only", () -> value.enabled, value::toggle, settings);
    }
}
