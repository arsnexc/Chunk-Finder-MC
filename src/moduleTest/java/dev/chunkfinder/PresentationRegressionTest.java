package dev.chunkfinder;

import com.google.gson.JsonParser;
import dev.chunkfinder.core.BaseClues;
import dev.chunkfinder.core.EspPalette;
import dev.chunkfinder.settings.BaseFinderConfig;
import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.settings.PaletteConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

final class PresentationRegressionTest {
    private static int assertions;
    static int run() throws Exception { assertions = 0; clues(); colors(); settings(); return assertions; }
    private static void clues() {
        BaseClues structure = new BaseClues();
        for (int x = 0; x < 10; x++) {
            structure.observe(x, 64, 1, BaseClues.Family.STORAGE, 4, false);
            structure.observe(x, 65, 2, BaseClues.Family.LIGHT, 2, false);
            structure.observe(x, 64, 3, BaseClues.Family.WORK, 3, false);
        }
        check(structure.results(10, true).isEmpty(), "ordinary structure loot/work/light blocks cannot pass strict mode even at maximum sensitivity");
        check(!structure.results(10, false).isEmpty(), "loose activity hints remain an explicit option");
        BaseClues onlySpecial = new BaseClues();
        for (int x = 0; x < 10; x++) onlySpecial.observe(x, 64, 0, BaseClues.Family.SPECIAL, 14, true);
        check(onlySpecial.results(10, true).isEmpty(), "one evidence family is insufficient even with many strong blocks");
        for (int origin : new int[]{-9, -1, 7, 15}) {
            BaseClues base = new BaseClues();
            base.observe(origin, 65, 1, BaseClues.Family.SPECIAL, 14, true);
            base.observe(origin + 1, 65, 1, BaseClues.Family.STORAGE, 4, false);
            base.observe(origin + 2, 65, 1, BaseClues.Family.WORK, 3, false);
            var results = base.results(5, true);
            check(results.size() == 1, "overlapping windows retain a compact setup across positive/negative cell boundaries without duplicates");
            var hit = results.getFirst();
            check(hit.minX() == origin && hit.maxX() == origin + 2 && hit.minY() == 65 && hit.maxY() == 65, "bounds describe evidence, not an entire chunk");
            check(hit.families() == 3 && hit.blocks() == 3 && hit.score() == 21, "family diversity and evidence score are retained");
        }
        BaseClues separated = new BaseClues();
        separated.observe(1, 10, 1, BaseClues.Family.SPECIAL, 14, true);
        separated.observe(2, 100, 1, BaseClues.Family.STORAGE, 4, false);
        separated.observe(3, 100, 1, BaseClues.Family.WORK, 3, false);
        check(separated.results(10, true).isEmpty(), "unrelated structures at different heights cannot combine into a false base");
        BaseClues many = new BaseClues();
        for (int y = 0; y < 200; y += 40) {
            many.observe(1, y, 1, BaseClues.Family.SPECIAL, 14, true);
            many.observe(2, y, 1, BaseClues.Family.STORAGE, 4, false);
            many.observe(3, y, 1, BaseClues.Family.WORK, 3, false);
        }
        check(many.results(5, true).size() == 4, "per-chunk cluster output is capped");
        BaseClues west=new BaseClues(),east=new BaseClues();
        west.observe(15,64,8,BaseClues.Family.SPECIAL,14,true,"beacon");
        east.observe(16,64,8,BaseClues.Family.STORAGE,4,false,"storage");east.observe(17,64,8,BaseClues.Family.WORK,3,false,"workstation");
        check(west.results(5,true).isEmpty()&&east.results(5,true).isEmpty(),"split evidence cannot qualify in either chunk alone");
        BaseClues.Index index=new BaseClues.Index();index.put(0,0,west);index.put(1,0,east);
        var joined=index.results(5,true);check(joined.size()==1,"cross-chunk evidence becomes one deduplicated clue");
        check(joined.getFirst().blocks()==3&&joined.getFirst().explanation().contains("beacon")&&joined.getFirst().explanation().contains("storage"),"cross-chunk score has no double counting and explains its signals");
        index.remove(1,0);check(index.results(5,true).isEmpty(),"unloading a contributing chunk invalidates the combined clue");
        index.put(1,0,east);index.retain(Set.of(BaseClues.Index.key(1,0)));check(index.results(5,true).isEmpty(),"range pruning cannot leave old neighboring evidence");
        index.clear();check(index.chunkCount()==0,"world reset clears the global evidence index");
    }
    private static void colors() {
        check(EspPalette.parse("#12ABef").orElseThrow() == 0xFF12ABEF, "RGB parsing is case-independent with opaque alpha");
        for (String bad : new String[]{"", "#123", "#12345678", "garbage", "#GG0000", null}) check(EspPalette.parse(bad).isEmpty(), "invalid color is rejected instead of becoming black");
        var original = new EspPalette.Snapshot(null, Map.of("minecraft:diamond_ore", 0xFFFF0000, "missing:ore", 0xFF00FFFF));
        var draft = new EspPalette.Draft(original);
        draft.set("", 0xFF00FF00); draft.set("minecraft:gold_ore", 0xFF0000FF);
        check(original.base() == null && !original.targets().containsKey("minecraft:gold_ore"), "color edits remain a draft until Apply");
        check(draft.color("minecraft:diamond_ore", 0) == 0xFFFF0000, "per-block override wins over module color");
        check(draft.color("minecraft:stone", 0) == 0xFF00FF00, "targets without an override inherit module color");
        draft.reset("minecraft:diamond_ore"); check(draft.color("minecraft:diamond_ore", 0) == 0xFF00FF00, "reset target falls back to module color");
        draft.reset(""); check(draft.color("minecraft:stone", 0xFF778899) == 0xFF778899, "reset module restores automatic type fallback");
        check(draft.snapshot().targets().containsKey("missing:ore"), "unavailable target colors are retained");
        draft.resetAll(); check(draft.snapshot().targets().isEmpty() && draft.snapshot().base() == null, "reset all is explicit and complete");
        try { original.targets().put("minecraft:stone", 0); throw new AssertionError("mutable palette snapshot"); }
        catch (UnsupportedOperationException expected) { assertions++; }
    }
    private static void settings() throws Exception {
        Path parent = Path.of("build/module-regression"); Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "presentation-");
        try {
            Path baseFile = directory.resolve("chunkfinder.json");
            Files.writeString(baseFile, "{\"enabled\":true,\"sensitivity\":7,\"simulationDistance\":5,\"other\":19}");
            BaseFinderConfig base = new BaseFinderConfig(baseFile);
            check(base.enabled() && base.sensitivity() == 7 && base.radius() == 5, "base finder retains legacy scalar settings");
            check(base.strict() && !base.geodes(), "strict clues default on; natural geodes are opt-in");
            base.geodes(true); base.strict(false);
            check(new BaseFinderConfig(baseFile).geodes() && !new BaseFinderConfig(baseFile).strict(), "geode/strict preferences persist");
            check(JsonParser.parseString(Files.readString(baseFile)).getAsJsonObject().get("other").getAsInt() == 19, "base config saves atomically without discarding unrelated keys");
            Path uiFile = directory.resolve("ui.json"); InterfaceConfig ui = new InterfaceConfig(uiFile);
            check(ui.hud() && !ui.backgrounds() && !ui.details() && ui.lines() == 6, "HUD defaults are compact and background-free");
            ui.minimized(Set.of("world", "movement", "bad id")); ui.contrast(true); ui.reducedMotion(true); ui.menuScale(125);
            ui.hudScale(999); ui.lines(999);
            InterfaceConfig restored = new InterfaceConfig(uiFile);
            check(restored.minimized().equals(Set.of("world", "movement")), "minimized categories persist with valid IDs");
            check(restored.contrast() && restored.reducedMotion() && restored.menuScale() == 125, "accessibility preferences survive reload");
            check(restored.hudScale() == 140 && restored.lines() == 10, "HUD scale and rows are bounded");
            ui.hudPosition(1000,500);ui.hudOpacity(45);ui.hideZeroRows(true);ui.rowVisible("blocks",false);ui.rowPinned("storage",true);ui.toggleFavorite("blocks/colors");ui.panelOrder(java.util.List.of("render","world","movement","player"));
            InterfaceConfig customized=new InterfaceConfig(uiFile);
            check(customized.hudX()==1000&&customized.hudY()==500&&customized.hudOpacity()==45&&customized.hideZeroRows(),"HUD position/opacity/hide-zero persist");
            check(customized.hiddenHud().contains("blocks")&&customized.pinnedHud().contains("storage"),"HUD row visibility and pins persist separately");
            check(customized.favorites().contains("blocks/colors")&&customized.panelOrder().getFirst().equals("render"),"favorites and snapped panel positions persist");
            Path paletteFile = directory.resolve("palette.json"); PaletteConfig palette = new PaletteConfig(paletteFile, 0xFFAAAAAA);
            palette.apply(palette.snapshot()); check(!Files.exists(paletteFile), "unchanged color Apply causes no write");
            palette.apply(new EspPalette.Snapshot(0xFF00FF00, Map.of("minecraft:diamond_ore", 0xFF0000FF, "missing:ore", 0xFF010203)));
            PaletteConfig reloaded = new PaletteConfig(paletteFile, 0xFFAAAAAA);
            check(reloaded.color("minecraft:diamond_ore") == 0xFF0000FF && reloaded.color("minecraft:stone") == 0xFF00FF00, "module and per-block colors persist independently");
            check(reloaded.snapshot().targets().containsKey("missing:ore"), "palette reload preserves missing-mod identifiers");
            Files.writeString(paletteFile, "{\"base\":\"#bad\",\"targets\":{\"minecraft:stone\":\"#123456\",\"bad id\":\"#FFFFFF\",\"test:bad\":12}}");
            PaletteConfig malformed = new PaletteConfig(paletteFile, 0xFFABCDEF);
            check(malformed.color(null) == 0xFFABCDEF && malformed.snapshot().targets().equals(Map.of("minecraft:stone", 0xFF123456)), "malformed color entries cannot corrupt the palette");
        } finally {
            try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
