package dev.chunkfinder.ui;

import dev.chunkfinder.ui.ClickGui.EntryBox;
import dev.chunkfinder.ui.ClickGui.Panel;
import dev.chunkfinder.ui.ClickGui.SettingBox;
import dev.chunkfinder.ui.UiGeometry.Rect;
import dev.chunkfinder.ui.UiTestFixtures.Fixture;

/** Dependency-free executable tests. Run via uiRegressionTest or scripts/test-ui.sh. */
public final class UiRegressionTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        fontResources();
        geometry();
        layoutAtGuiScales();
        toggleAndExpansion();
        sliderInput();
        independentScrollAndClipping();
        scrollbarCapture();
        keyboardNavigation();
        narrowWindows();
        pickerLayout();
        rendering();
        System.out.println("UI regression tests passed (" + assertions + " assertions).");
        System.out.println("These are headless layout/input/rendering checks, not a Minecraft/Fabric or in-game test.");
    }

    private static void fontResources() throws Exception {
        check(AwtUiCanvas.fontResource().endsWith("assets/chunkfinder/font/inter.ttf"),
                "TTF identifier resolves with Minecraft's single font/ prefix");
        java.awt.Font font = AwtUiCanvas.loadUiFont();
        for (int codePoint = 32; codePoint <= 126; codePoint++)
            check(font.canDisplay(codePoint), "UI font covers printable ASCII " + codePoint);
        for (int codePoint : new int[]{0x00D7, 0x00B7, 0x2026})
            check(font.canDisplay(codePoint), "UI font covers close, separator and ellipsis glyphs");
    }

    private static void geometry() {
        Rect bounds = new Rect(10, 20, 30, 40);
        check(bounds.contains(10, 20), "top-left pixel belongs to rect");
        check(!bounds.contains(40, 20), "right edge is exclusive");
        check(!bounds.contains(10, 60), "bottom edge is exclusive");
        check(bounds.intersect(new Rect(0, 0, 5, 5)).width() == 0, "disjoint clip has no width");
        check(bounds.intersect(new Rect(0, 0, 5, 5)).height() == 0, "disjoint clip has no height");
        Rect track = new Rect(100, 100, 101, 2);
        eq(2, UiGeometry.sliderValue(100, track, 2, 12), "slider minimum");
        eq(7, UiGeometry.sliderValue(150, track, 2, 12), "slider midpoint");
        eq(12, UiGeometry.sliderValue(200, track, 2, 12), "slider maximum");
        eq(2, UiGeometry.sliderValue(-10000, track, 2, 12), "negative drag clamps");
        eq(12, UiGeometry.sliderValue(10000, track, 2, 12), "positive drag clamps");
        eq(2, UiGeometry.sliderValue(Double.NaN, track, 2, 12), "non-finite pointer is safe");
        for (int value = 2; value <= 12; value++)
            eq(value, UiGeometry.sliderValue(UiGeometry.sliderPosition(value, track, 2, 12), track, 2, 12), "slider roundtrip");
        Rect scrollTrack = new Rect(10, 20, 2, 100);
        Rect thumb = UiGeometry.verticalThumb(scrollTrack, 500, 100, 400);
        eq(20, thumb.height(), "proportional scrollbar");
        eq(scrollTrack.bottom(), thumb.bottom(), "scrollbar reaches bottom");
        eq(400, (int) UiGeometry.scrollFromThumb(thumb.y(), 0, scrollTrack, thumb.height(), 400), "thumb to offset");
        eq(0, UiGeometry.verticalThumb(scrollTrack, 100, 100, 0).height(), "no unnecessary scrollbar");
        eq(0, (int) UiGeometry.scrollFromThumb(200, 0, new Rect(0, 0, 1, 0), 0, 400), "zero track does not divide by zero");
    }

    private static void layoutAtGuiScales() {
        for (int[] size : new int[][]{{256, 144}, {320, 240}, {427, 240}, {640, 360}, {854, 480}, {960, 540}}) {
            Fixture fixture = UiTestFixtures.large();
            fixture.gui().resize(size[0], size[1]);
            ClickGui.Frame frame = fixture.gui().frame();
            check(frame.viewport().x() >= 0 && frame.viewport().right() <= size[0], "horizontal viewport stays on screen");
            check(frame.viewport().y() >= 0 && frame.viewport().bottom() < size[1] - 16, "footer never overlaps panels");
            check(frame.close().y() >= 0 && frame.close().right() <= size[0], "close button remains accessible");
            for (Panel panel : frame.panels()) {
                check(panel.header().bottom() < panel.body().y(), "header/body gap");
                check(panel.body().bottom() <= size[1] - 31, "body above horizontal navigation");
                check(panel.clip().x() > panel.body().x() && panel.clip().right() < panel.body().right(), "content clips inside body");
                int lastBottom = Integer.MIN_VALUE;
                for (EntryBox entry : panel.entries()) {
                    check(entry.header().y() >= lastBottom, "module cards do not overlap");
                    lastBottom = entry.bounds().bottom();
                    for (SettingBox setting : entry.settings()) {
                        check(setting.bounds().y() >= entry.header().bottom(), "settings below module header");
                        check(setting.bounds().bottom() <= entry.bounds().bottom(), "settings inside expanded card");
                        check(setting.bounds().x() >= entry.bounds().x() && setting.bounds().right() <= entry.bounds().right(), "settings fit card width");
                    }
                }
            }
        }
    }

    private static void toggleAndExpansion() {
        Fixture fixture = UiTestFixtures.large();
        ClickGui gui = fixture.gui();
        EntryBox entry = first(gui);
        check(!entry.settings().isEmpty(), "first controls initially visible");
        clickHeader(gui, entry, 1);
        eq(0, first(gui).settings().size(), "right-click collapses");
        eq(0, fixture.values().getFirst().toggles, "right-click never toggles module");
        entry = first(gui);
        gui.mouseClicked(entry.expander().x() + 4, entry.expander().y() + 10, 0);
        check(!first(gui).settings().isEmpty(), "chevron expands");
        eq(0, fixture.values().getFirst().toggles, "chevron never toggles module");
        clickHeader(gui, first(gui), 0);
        check(fixture.values().getFirst().enabled, "left-click toggles module");
        Rect pill = first(gui).pill();
        gui.mouseClicked(pill.x() + 3, pill.y() + 3, 0);
        check(!fixture.values().getFirst().enabled, "pill toggles off");
        eq(2, fixture.values().getFirst().toggles, "exactly one callback per click");
        SettingBox option = first(gui).settings().get(1);
        gui.mouseClicked(option.bounds().x() + 5, option.bounds().y() + 8, 1);
        check(fixture.values().getFirst().option, "right-click setting does not change it");
        gui.mouseClicked(option.bounds().x() + 5, option.bounds().y() + 8, 0);
        check(!fixture.values().getFirst().option, "left-click setting changes it");
        check(!gui.mouseClicked(-100, -100, 0), "outside click ignored");
        check(!gui.mouseClicked(pill.x(), pill.y(), 2), "middle click ignored");
        Rect close = gui.frame().close();
        gui.mouseClicked(close.x() + 3, close.y() + 3, 0);
        eq(1, fixture.closed().get(), "close delegates to screen");
    }

    private static void sliderInput() {
        Fixture fixture = UiTestFixtures.large();
        ClickGui gui = fixture.gui();
        UiTestFixtures.Value value = fixture.values().getFirst();
        SettingBox slider = first(gui).settings().getFirst();
        gui.mouseClicked(slider.bounds().x() + 3, slider.bounds().y() + 4, 0);
        eq(5, value.number, "clicking slider label must not jump to minimum");
        eq(0, value.writes, "label click does not write config");
        Rect track = slider.sliderTrack();
        gui.mouseClicked(track.x(), track.y(), 0);
        eq(1, value.number, "track press starts drag");
        gui.mouseDragged(10000, -100, 0);
        eq(10, value.number, "pointer capture continues outside panel");
        int writes = value.writes;
        gui.mouseDragged(10001, -100, 0);
        eq(writes, value.writes, "same integer value is not saved twice");
        check(gui.mouseReleased(0), "release consumed");
        check(!gui.mouseDragged(-100, -100, 0), "released drag not active");
        eq(10, value.number, "released drag cannot change value");
        gui.mouseClicked(track.x(), track.y(), 0);
        gui.resize(854, 480);
        check(!gui.mouseDragged(10000, 10000, 0), "resize cancels stale drag geometry");
        eq(1, value.number, "resize does not change setting");
        slider = first(gui).settings().getFirst();
        gui.mouseClicked(slider.sliderTrack().x(), slider.sliderTrack().y(), 0);
        gui.cancelDrag();
        check(!gui.mouseDragged(10000, 0, 0), "screen removal cancels drag");
    }

    private static void independentScrollAndClipping() {
        Fixture fixture = UiTestFixtures.large();
        ClickGui gui = fixture.gui();
        gui.resize(640, 240);
        Panel first = gui.frame().panels().getFirst();
        gui.mouseScrolled(first.body().x() + 30, first.body().y() + 30, 0, -2.5);
        check(gui.frame().panels().getFirst().scroll() > 0, "hovered panel scrolls");
        eq(0, (int) gui.frame().panels().get(1).scroll(), "neighbor does not scroll");
        first = gui.frame().panels().getFirst();
        int toggles = fixture.values().stream().mapToInt(value -> value.toggles).sum();
        gui.mouseClicked(first.clip().x() + 30, first.clip().y() - 1, 0);
        gui.mouseClicked(first.clip().x() + 30, first.clip().bottom(), 0);
        eq(toggles, fixture.values().stream().mapToInt(value -> value.toggles).sum(), "clipped rows never receive clicks");
        gui.mouseScrolled(first.body().x() + 30, first.body().y() + 30, 0, -10000);
        first = gui.frame().panels().getFirst();
        eq(first.maxScroll(), (int) first.scroll(), "wheel clamps at bottom");
        gui.resize(854, 540);
        first = gui.frame().panels().getFirst();
        check(first.scroll() <= first.maxScroll(), "resize clamps saved scroll");
        gui.mouseScrolled(first.body().x() + 30, first.body().y() + 30, 0, 10000);
        eq(0, (int) gui.frame().panels().getFirst().scroll(), "wheel clamps at top");
        clickHeader(gui, first(gui), 1);
        check(gui.frame().panels().getFirst().scroll() <= gui.frame().panels().getFirst().maxScroll(), "collapse keeps scroll valid");
        check(!gui.mouseScrolled(0, 0, Double.NaN, 1), "invalid wheel input ignored");
    }

    private static void scrollbarCapture() {
        Fixture fixture = UiTestFixtures.large();
        ClickGui gui = fixture.gui();
        gui.resize(640, 240);
        Panel panel = gui.frame().panels().getFirst();
        Rect thumb = panel.scrollThumb();
        gui.mouseClicked(thumb.x(), thumb.y() + 4, 0);
        gui.mouseDragged(thumb.x(), 10000, 0);
        eq(gui.frame().panels().getFirst().maxScroll(), (int) gui.frame().panels().getFirst().scroll(), "scrollbar drag reaches last row");
        gui.mouseDragged(thumb.x(), -10000, 0);
        eq(0, (int) gui.frame().panels().getFirst().scroll(), "scrollbar drag clamps at top");
        gui.mouseReleased(0);
        eq(0, (int) gui.frame().panels().get(1).scroll(), "scrollbar capture stays in one column");
    }

    private static void keyboardNavigation() {
        Fixture fixture = UiTestFixtures.large();
        ClickGui gui = fixture.gui();
        check(!gui.keyPressed(32, false), "Space without focus does nothing");
        gui.keyPressed(258, false);
        gui.keyPressed(32, false);
        check(fixture.values().getFirst().enabled, "keyboard toggles focused module");
        gui.keyPressed(263, false);
        eq(0, first(gui).settings().size(), "left arrow collapses module");
        gui.keyPressed(262, false);
        check(!first(gui).settings().isEmpty(), "right arrow expands module");
        gui.keyPressed(258, false);
        gui.keyPressed(262, false);
        eq(6, fixture.values().getFirst().number, "right arrow increments slider");
        gui.keyPressed(268, false);
        eq(1, fixture.values().getFirst().number, "Home sets minimum");
        gui.keyPressed(269, false);
        eq(10, fixture.values().getFirst().number, "End sets maximum");
        gui.keyPressed(258, false);
        gui.keyPressed(257, false);
        check(!fixture.values().getFirst().option, "Enter activates inline toggle");
        gui.keyPressed(258, false);
        gui.keyPressed(257, false);
        eq(1, fixture.values().getFirst().actions, "Enter invokes action once");
        gui.keyPressed(258, true);
        gui.keyPressed(32, false);
        check(fixture.values().getFirst().option, "Shift-Tab goes backwards");
    }

    private static void narrowWindows() {
        Fixture fixture = UiTestFixtures.large();
        ClickGui gui = fixture.gui();
        gui.resize(320, 240);
        ClickGui.Frame frame = gui.frame();
        check(frame.maxHorizontalScroll() > 0, "small window uses horizontal navigation, not tiny text");
        check(frame.panels().getLast().header().x() > frame.viewport().right(), "last category starts outside viewport");
        EntryBox hidden = frame.panels().getLast().entries().getFirst();
        clickHeader(gui, hidden, 0);
        check(!fixture.values().get(36).enabled, "offscreen module cannot be clicked");
        gui.keyPressed(258, true);
        frame = gui.frame();
        check(frame.panels().getLast().header().right() <= frame.viewport().right(), "keyboard reveals last category");
        check(frame.panels().getLast().entries().getLast().header().bottom() <= frame.panels().getLast().clip().bottom(), "keyboard reveals last module vertically");
        Rect thumb = frame.horizontalThumb();
        gui.mouseClicked(thumb.x() + 3, thumb.y() + 1, 0);
        gui.mouseDragged(-10000, thumb.y(), 0);
        gui.mouseReleased(0);
        frame = gui.frame();
        eq(frame.viewport().x(), frame.panels().getFirst().header().x(), "horizontal scrollbar reaches first column");
        gui.mouseScrolled(0, 0, 0, -10000);
        frame = gui.frame();
        eq(frame.viewport().right(), frame.panels().getLast().header().right(), "background wheel reaches last column");
        gui.resize(960, 540);
        eq(0, gui.frame().maxHorizontalScroll(), "large resize removes unnecessary horizontal scroll");
    }

    private static void pickerLayout() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            PickerLayout layout = PickerLayout.at(size[0], size[1]);
            check(layout.panel().x() >= 0 && layout.panel().right() <= size[0], "picker never exceeds screen width");
            check(layout.panel().y() >= 0 && layout.panel().bottom() <= size[1], "picker never exceeds screen height");
            check(layout.search().bottom() < layout.list().y(), "search does not overlap rows");
            for (int row = 0; row < layout.pageSize(); row++)
                check(layout.row(row).bottom() <= layout.list().bottom(), "picker rows fit at all supported GUI scales");
            check(layout.list().bottom() < layout.previous().y(), "picker rows do not overlap navigation");
            eq(1, layout.pageCount(0), "empty query results have one empty page");
            eq(0, layout.clampPage(100, 0), "empty query clamps page");
            eq(0, layout.clampPage(-10, 100), "negative pages clamp");
            eq(1, layout.pageCount(layout.pageSize()), "full page not followed by empty page");
            eq(2, layout.pageCount(layout.pageSize() + 1), "partial final page remains reachable");
            int selected = 27;
            int page = layout.pageContaining(selected, 100);
            check(page * layout.pageSize() <= selected && selected < (page + 1) * layout.pageSize(),
                    "resizing keeps the keyboard-selected block on the visible page");
        }
    }

    private static void rendering() throws Exception {
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {960, 540}}) {
            try (AwtUiCanvas canvas = new AwtUiCanvas(size[0], size[1], 1)) {
                ClickGui gui = UiTestFixtures.preview();
                gui.resize(size[0], size[1]);
                gui.render(canvas, -1, -1);
                eq(0, canvas.clipDepth(), "all render scissor stacks balanced");
                check(canvas.textWidth(UiTheme.fit(canvas, "Ancient Debris Finder", 35)) <= 35, "labels fit narrow columns");
                eq(0, UiTheme.fit(canvas, "text", 0).length(), "zero-width label is empty");
                String unicode = UiTheme.fit(canvas, "ABC 🧭 long text", 29);
                if (!unicode.isEmpty()) check(!Character.isHighSurrogate(unicode.charAt(unicode.length() - 1)), "ellipsis does not split surrogate pair");
                int visible = 0;
                for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) {
                    int pixel = canvas.image().getRGB(x, y);
                    int red = pixel >>> 16 & 255, green = pixel >>> 8 & 255, blue = pixel & 255;
                    if (red != green || green != blue) throw new AssertionError("Unexpected colored accent");
                    if (red > 40) visible++;
                }
                check(visible > 1000, "headless renderer draws actual content");
            }
        }
    }

    private static EntryBox first(ClickGui gui) { return gui.frame().panels().getFirst().entries().getFirst(); }
    private static void clickHeader(ClickGui gui, EntryBox entry, int button) {
        gui.mouseClicked(entry.header().x() + 25, entry.header().y() + 10, button);
    }
    private static void eq(int expected, int actual, String message) {
        check(expected == actual, message + " (expected " + expected + ", got " + actual + ")");
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
