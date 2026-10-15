package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

final class UiEnhancementTest {
    private static int assertions;
    static int run() throws Exception { assertions = 0; panels(); motion(); hud(); colors(); return assertions; }
    private static void panels() throws Exception {
        var fixture = UiTestFixtures.large(); var gui = fixture.gui();
        var panel = gui.frame().panels().getFirst(); String id = panel.category().id();
        AtomicReference<Set<String>> saved = new AtomicReference<>(Set.of());
        gui.restorePanels(Set.of(), saved::set);
        gui.mouseScrolled(panel.clip().x() + 8, panel.clip().y() + 20, 0, -5);
        double scroll = gui.frame().panels().getFirst().scroll();
        Rect row = gui.frame().panels().getFirst().entries().getFirst().header();
        Rect header = gui.headerBounds(id);
        gui.mouseClicked(header.x() + 8, header.y() + 8, 0);
        check(gui.minimizedPanels().contains(id) && saved.get().contains(id), "header minimizes and persists the category");
        check(gui.frame().panels().getFirst().entries().isEmpty() && gui.frame().panels().getFirst().body().height() == 0, "minimized panels contain no hidden interactive rows");
        gui.mouseClicked(row.x() + 20, row.y() + 10, 0);
        check(fixture.values().stream().noneMatch(v -> v.enabled), "minimizing cannot toggle modules or leave old hitboxes active");
        gui.keyPressed(262, false);
        check(!gui.minimizedPanels().contains(id) && saved.get().isEmpty(), "keyboard Right restores a focused minimized panel");
        check(gui.frame().panels().getFirst().scroll() == scroll, "minimize/restore preserves its scroll position");
        gui.keyPressed(263, false); check(gui.focusedLabel().contains("minimized"), "narration exposes collapsed state");
        gui.keyPressed(257, false); check(gui.focusedLabel().contains("expanded"), "Enter restores a panel without toggling a module");
        gui.restorePanels(Set.of(id), saved::set);
        try (var canvas = new AwtUiCanvas(640, 360, 1)) { gui.presentation(true, true, true); gui.render(canvas, 0, 0); check(canvas.clipDepth() == 0, "high-contrast minimized rendering balances clipping"); }
    }
    private static void motion() {
        UiMotion animation = new UiMotion();
        check(animation.value("toggle", false, 0, false) == 0, "animation starts from current state");
        check(animation.value("toggle", true, 1, false) == 0, "toggle transition does not jump");
        double middle = animation.value("toggle", true, 70_000_001, false);
        check(middle > 0 && middle < 1, "toggle thumb interpolates between fixed hitbox endpoints");
        check(animation.value("toggle", true, 200_000_001, false) == 1, "animation converges to its target");
        check(animation.value("toggle", false, 200_000_002, true) == 0, "reduced motion snaps immediately");
        check(UiMotion.mix(0xFF000000, 0xFFFFFFFF, .5) == 0xFF808080, "color interpolation preserves alpha and neutral palette");
        check(UiMotion.fittedScale(150, 320, 240) == 1.25, "zoom fits narrow windows rather than shrinking hitboxes out of view");
        check(UiMotion.fittedScale(150, 640, 360) == 1.5, "large menus honor requested zoom");
    }
    private static void hud() {
        class Spy implements UiCanvas {
            final List<Rect> fills = new ArrayList<>(); final List<String> text = new ArrayList<>(); int clips;
            public void fill(int l, int t, int r, int b, int c) { fills.add(new Rect(l, t, r - l, b - t)); }
            public void text(String s, int x, int y, int c) { text.add(s); }
            public int textWidth(String s) { return s.codePointCount(0, s.length()) * 5; }
            public void pushClip(Rect r) { clips++; }
            public void popClip() { clips--; }
        }
        List<CompactHud.Row> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) rows.add(new CompactHud.Row("Block ESP " + i, "128", "Detail coordinates " + i, 0xFFFFAA00));
        Spy canvas = new Spy(); Rect bounds = CompactHud.draw(canvas, 320, 240, rows, 3, false, false);
        check(bounds.height() <= 44 && bounds.right() <= 320, "compact HUD has a strict row/viewport bound");
        check(canvas.fills.stream().allMatch(r -> r.width() <= 2), "background-free mode emits only tiny accent marks, not cards");
        check(canvas.text.stream().anyMatch(s -> s.contains("+8 more")), "overflow is summarized instead of covering the screen");
        check(canvas.clips == 0, "HUD clipping is balanced");
        Spy withBackground = new Spy(); CompactHud.draw(withBackground, 320, 240, rows, 3, true, true);
        check(withBackground.fills.stream().anyMatch(r -> r.width() > 20), "optional HUD background remains available");
        Spy empty = new Spy(); CompactHud.draw(empty, 320, 240, List.of(), 6, false, true);
        check(empty.fills.isEmpty() && empty.text.isEmpty(), "empty HUD leaves no blank panel");
    }
    private static void colors() {
        for (int[] size : new int[][]{{320,240},{427,240},{640,360},{854,480}}) {
            var ui = ColorEditorLayout.at(size[0], size[1]); Rect p = ui.panel();
            check(p.x() >= 0 && p.y() >= 0 && p.right() <= size[0] && p.bottom() <= size[1], "color editor fits supported GUI scales");
            for (Rect box : List.of(ui.scope(), ui.hex(), ui.preview(), ui.reset(), ui.resetAll(), ui.cancel(), ui.apply()))
                check(box.x() >= p.x() && box.right() <= p.right() && box.y() >= p.y() && box.bottom() <= p.bottom(), "color control stays inside its panel");
            check(ui.hex().right() < ui.preview().x(), "hex text and preview never overlap");
            check(ColorEditorLayout.sliderHit(ui.blue()).bottom() < ui.reset().y(), "RGB slider cannot overlap reset controls");
            check(ui.reset().bottom() < ui.apply().y(), "reset and Apply have distinct hitboxes");
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
