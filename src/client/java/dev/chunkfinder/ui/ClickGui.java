package dev.chunkfinder.ui;

import dev.chunkfinder.ui.ClickGuiModel.Action;
import dev.chunkfinder.ui.ClickGuiModel.Category;
import dev.chunkfinder.ui.ClickGuiModel.Entry;
import dev.chunkfinder.ui.ClickGuiModel.Setting;
import dev.chunkfinder.ui.ClickGuiModel.Slider;
import dev.chunkfinder.ui.ClickGuiModel.Toggle;
import dev.chunkfinder.ui.UiGeometry.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.chunkfinder.ui.UiGeometry.clamp;
import static dev.chunkfinder.ui.UiTheme.*;

/**
 * Compact category click-GUI. Rendering and input share one layout, including all clip bounds.
 * No Minecraft state, disk IO, or module behavior lives here.
 */
public final class ClickGui {
    static final int MARGIN = 14;
    static final int PANEL_GAP = 12;
    static final int HEADER_HEIGHT = 32;
    static final int HEADER_GAP = 6;
    static final int ROW_HEIGHT = 23;
    static final int ROW_GAP = 3;
    static final int PADDING = 6;

    private final List<Category> categories;
    private final Runnable close;
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, Double> scroll = new HashMap<>();
    private int width = 640;
    private int height = 360;
    private double horizontalScroll;
    private Drag drag;
    private Focus focus;
    private boolean keyboardFocus;
    private String notice = "";
    private long noticeUntil;

    public ClickGui(List<Category> categories, Runnable close) {
        this.categories = List.copyOf(categories);
        this.close = close;
        Set<String> ids = new HashSet<>();
        Set<String> categoryIds = new HashSet<>();
        for (Category category : categories) {
            if (!categoryIds.add(category.id())) throw new IllegalArgumentException("Duplicate category ID");
            for (Entry entry : category.entries()) {
                if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate module ID");
                Set<String> settingIds = new HashSet<>();
                for (Setting setting : entry.settings())
                    if (!settingIds.add(setting.id())) throw new IllegalArgumentException("Duplicate setting ID");
            }
            // Reveal one set of controls per category without changing any enabled setting.
            if (!category.entries().isEmpty()) expanded.add(category.entries().getFirst().id());
        }
    }

    public void resize(int width, int height) {
        if (this.width != width || this.height != height) cancelDrag();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        frame(); // Clamp offsets after a GUI-scale/window-size change.
    }

    public void notice(String message) {
        notice = message;
        noticeUntil = System.nanoTime() + 4_000_000_000L;
    }

    public void cancelDrag() { drag = null; }

    public void render(UiCanvas canvas, int mouseX, int mouseY) {
        Frame frame = frame();
        text(canvas, "CHUNK FINDER", frame.titleX(), frame.titleY(), 120, MUTED);
        Rect closeBounds = frame.close();
        if (closeBounds.contains(mouseX, mouseY)) rounded(canvas, closeBounds, 4, ROW_HOVER);
        centered(canvas, "×", closeBounds, MUTED);
        canvas.pushClip(frame.viewport());
        for (Panel panel : frame.panels()) drawPanel(canvas, panel, mouseX, mouseY);
        canvas.popClip();

        if (frame.maxHorizontalScroll() > 0) {
            rounded(canvas, frame.horizontalTrack(), 2, 0xFF2B2B2B);
            rounded(canvas, frame.horizontalThumb(), 2,
                    frame.horizontalTrack().contains(mouseX, mouseY) ? 0xFF8B8B8B : 0xFF555555);
        }
        String hint = hintAt(frame, mouseX, mouseY);
        if (System.nanoTime() < noticeUntil) hint = notice;
        if (hint.isEmpty()) hint = "Click to toggle · Right-click for settings · Scroll a panel · Esc to close";
        if (frame.maxHorizontalScroll() > 0 && hintAt(frame, mouseX, mouseY).isEmpty()
                && System.nanoTime() >= noticeUntil)
            hint = "Scroll outside panels or drag the bottom bar for more columns · Tab to navigate";
        text(canvas, hint, MARGIN, height - 16, width - MARGIN * 2, QUIET);
    }

    private void drawPanel(UiCanvas canvas, Panel panel, int mouseX, int mouseY) {
        shadow(canvas, panel.header(), 8);
        outlined(canvas, panel.header(), 8, 0xFF222222, 0xF2191919);
        Rect inset = panel.header().inset(5);
        outlined(canvas, inset, 5, 0xFF4A4A4A, 0xFF303030);
        canvas.fill(inset.x() + 5, inset.y() + 1, inset.right() - 5, inset.y() + 2, 0xFF393939);
        int labelWidth = canvas.textWidth(panel.category().label());
        int labelX = inset.x() + (inset.width() - labelWidth - 15) / 2;
        icon(canvas, panel.category().icon(), labelX, inset.y() + 7);
        canvas.text(panel.category().label(), labelX + 15, inset.y() + 6, TEXT);

        shadow(canvas, panel.body(), 9);
        outlined(canvas, panel.body(), 9, BORDER, PANEL);
        canvas.pushClip(panel.clip());
        for (EntryBox entry : panel.entries()) {
            if (entry.bounds().intersect(panel.clip()).height() == 0) continue;
            boolean isExpanded = expanded.contains(entry.entry().id());
            boolean enabled = entry.entry().enabled().getAsBoolean();
            boolean hovered = entry.header().contains(mouseX, mouseY) && panel.clip().contains(mouseX, mouseY);
            boolean focused = keyboardFocus && new Focus(entry.entry().id(), null).equals(focus);
            if (enabled || isExpanded || hovered || focused) {
                outlined(canvas, entry.bounds(), 5, focused ? FOCUS : enabled ? ACTIVE_BORDER : BORDER,
                        isExpanded ? 0xFF202020 : hovered ? ROW_HOVER : ACTIVE);
                if (isExpanded && (enabled || hovered))
                    rounded(canvas, entry.header().inset(1), 4, enabled ? ACTIVE : ROW_HOVER);
            }
            Rect header = entry.header();
            if (!entry.entry().settings().isEmpty())
                chevron(canvas, header.x() + 5, header.y() + 10, isExpanded, hovered ? TEXT : QUIET);
            text(canvas, entry.entry().label(), header.x() + 15, header.y() + 7,
                    header.width() - 42, enabled ? TEXT : hovered || isExpanded ? 0xFFC5C5C5 : MUTED);
            toggle(canvas, entry.pill(), enabled);
            for (SettingBox setting : entry.settings()) drawSetting(canvas, entry.entry(), setting, panel.clip(), mouseX, mouseY);
        }
        canvas.popClip();
        if (panel.maxScroll() > 0) {
            rounded(canvas, panel.scrollTrack(), 1, 0xFF292929);
            rounded(canvas, panel.scrollThumb(), 1, 0xFF606060);
        }
    }

    private void drawSetting(UiCanvas canvas, Entry entry, SettingBox box, Rect clip, int mouseX, int mouseY) {
        Rect bounds = box.bounds();
        if (bounds.intersect(clip).height() == 0) return;
        boolean hovered = bounds.contains(mouseX, mouseY) && clip.contains(mouseX, mouseY);
        boolean focused = keyboardFocus && new Focus(entry.id(), box.setting().id()).equals(focus);
        if (focused) outlined(canvas, bounds, 3, FOCUS, ROW);
        else if (hovered && !(box.setting() instanceof Slider)) rounded(canvas, bounds, 3, ROW_HOVER);

        if (box.setting() instanceof Toggle setting) {
            text(canvas, setting.label(), bounds.x() + 2, bounds.y() + 6, bounds.width() - 24, TEXT);
            toggle(canvas, new Rect(bounds.right() - 18, bounds.y() + 6, 16, 8), setting.enabled().getAsBoolean());
        } else if (box.setting() instanceof Slider setting) {
            String value = setting.displayValue();
            int valueWidth = Math.min(42, canvas.textWidth(value));
            text(canvas, setting.label(), bounds.x() + 2, bounds.y() + 4,
                    bounds.width() - valueWidth - 9, TEXT);
            text(canvas, value, bounds.right() - valueWidth - 2, bounds.y() + 4, valueWidth, MUTED);
            Rect track = box.sliderTrack();
            rounded(canvas, track, 1, 0xFF454545);
            int position = UiGeometry.sliderPosition(setting.value().getAsInt(), track, setting.min(), setting.max());
            fill(canvas, new Rect(track.x(), track.y(), position - track.x(), track.height()), 0xFFB8B8B8);
            rounded(canvas, new Rect(position - 2, track.y() - 1, 4, 4), 2, TEXT);
        } else if (box.setting() instanceof Action setting) {
            text(canvas, setting.label(), bounds.x() + 2, bounds.y() + 6, bounds.width() - 15, TEXT);
            chevron(canvas, bounds.right() - 9, bounds.y() + 9, false, MUTED);
            if (setting.detail() != null)
                text(canvas, setting.detail().get(), bounds.x() + 2, bounds.y() + 18, bounds.width() - 5, MUTED);
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 && button != 1) return false;
        Frame frame = frame();
        keyboardFocus = false;
        if (button == 0 && frame.close().contains(x, y)) { close.run(); return true; }
        if (button == 0 && frame.maxHorizontalScroll() > 0
                && new Rect(frame.horizontalTrack().x(), frame.horizontalTrack().y() - 3,
                frame.horizontalTrack().width(), 10).contains(x, y)) {
            Rect thumb = frame.horizontalThumb(), track = frame.horizontalTrack();
            double grab = thumb.contains(x, track.y()) ? x - thumb.x() : thumb.width() / 2.0;
            drag = new HorizontalDrag(track, thumb.width(), frame.maxHorizontalScroll(), grab);
            mouseDragged(x, y, button);
            return true;
        }
        if (!frame.viewport().contains(x, y)) return false;
        for (Panel panel : frame.panels()) {
            if (button == 0 && panel.maxScroll() > 0
                    && new Rect(panel.scrollTrack().x() - 2, panel.scrollTrack().y(), 6,
                    panel.scrollTrack().height()).contains(x, y)) {
                Rect thumb = panel.scrollThumb();
                double grab = thumb.contains(panel.scrollTrack().x(), y) ? y - thumb.y() : thumb.height() / 2.0;
                drag = new ScrollDrag(panel.category().id(), panel.scrollTrack(), thumb.height(), panel.maxScroll(), grab);
                mouseDragged(x, y, button);
                return true;
            }
            if (!panel.clip().contains(x, y)) continue;
            for (EntryBox box : panel.entries()) {
                if (box.header().contains(x, y)) {
                    focus = new Focus(box.entry().id(), null);
                    if (button == 1 || box.expander().contains(x, y)) expand(box.entry());
                    else box.entry().toggle().run();
                    return true;
                }
                for (SettingBox setting : box.settings()) {
                    if (!setting.bounds().contains(x, y)) continue;
                    if (button != 0) return true;
                    focus = new Focus(box.entry().id(), setting.setting().id());
                    if (setting.setting() instanceof Toggle toggle) toggle.toggle().run();
                    else if (setting.setting() instanceof Action action) action.run().run();
                    else if (setting.setting() instanceof Slider slider && setting.sliderHitBox().contains(x, y)) {
                        drag = new SliderDrag(slider, setting.sliderTrack());
                        mouseDragged(x, y, button);
                    }
                    return true;
                }
            }
            return true;
        }
        return frame.panels().stream().anyMatch(p -> p.header().contains(x, y) || p.body().contains(x, y));
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (button != 0 || drag == null) return false;
        if (drag instanceof SliderDrag slider) {
            slider.setting().set(UiGeometry.sliderValue(x, slider.track(), slider.setting().min(), slider.setting().max()));
        } else if (drag instanceof ScrollDrag vertical) {
            scroll.put(vertical.category(), UiGeometry.scrollFromThumb(y, vertical.grabOffset(),
                    vertical.track(), vertical.thumbSize(), vertical.max()));
        } else if (drag instanceof HorizontalDrag horizontal) {
            Rect transposed = new Rect(0, horizontal.track().x(), 1, horizontal.track().width());
            horizontalScroll = UiGeometry.scrollFromThumb(x, horizontal.grabOffset(), transposed,
                    horizontal.thumbSize(), horizontal.max());
        }
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || drag == null) return false;
        cancelDrag();
        return true;
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (drag != null) return true;
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) return false;
        Frame frame = frame();
        if (horizontal != 0 && frame.maxHorizontalScroll() > 0) {
            horizontalScroll = clamp(horizontalScroll - horizontal * 26, 0, frame.maxHorizontalScroll());
            return true;
        }
        if (frame.viewport().contains(x, y)) {
            for (Panel panel : frame.panels()) {
                if (panel.body().contains(x, y)) {
                    scroll.put(panel.category().id(), clamp(panel.scroll() - vertical * 26, 0, panel.maxScroll()));
                    return true;
                }
            }
        }
        if (frame.maxHorizontalScroll() > 0) {
            horizontalScroll = clamp(horizontalScroll - vertical * 32, 0, frame.maxHorizontalScroll());
            return true;
        }
        return false;
    }

    /** GLFW key numbers are passed by the Minecraft adapter; this core has no GLFW dependency. */
    public boolean keyPressed(int key, boolean shift) {
        if (key == 258 || key == 264 || key == 265) { // Tab / down / up
            cancelDrag();
            moveFocus(key == 265 || key == 258 && shift ? -1 : 1);
            return true;
        }
        if (focus == null) return false;
        FocusBox focused = focusBoxes(frame()).stream().filter(box -> box.focus().equals(focus)).findFirst().orElse(null);
        if (focused == null) return false;
        boolean activate = key == 257 || key == 335 || key == 32; // Enter / keypad Enter / Space
        keyboardFocus = true;
        if (focused.setting() instanceof Slider slider) {
            if (key == 263 || key == 262) slider.set(slider.value().getAsInt() + (key == 263 ? -1 : 1));
            else if (key == 268 || key == 269) slider.set(key == 268 ? slider.min() : slider.max());
            else return false;
        } else if (focused.setting() instanceof Toggle toggle && activate) toggle.toggle().run();
        else if (focused.setting() instanceof Action action && activate) action.run().run();
        else if (focused.setting() == null) {
            if (activate) focused.entry().toggle().run();
            else if (key == 262 || key == 263) {
                if (key == 262 && !focused.entry().settings().isEmpty()) expanded.add(focused.entry().id());
                else expanded.remove(focused.entry().id());
            } else return false;
        } else return false;
        ensureFocusVisible();
        return true;
    }

    private void moveFocus(int direction) {
        List<FocusBox> boxes = focusBoxes(frame());
        if (boxes.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).focus().equals(focus)) current = i;
        int next = current < 0 ? (direction < 0 ? boxes.size() - 1 : 0) : Math.floorMod(current + direction, boxes.size());
        focus = boxes.get(next).focus();
        keyboardFocus = true;
        ensureFocusVisible();
    }

    private void ensureFocusVisible() {
        Frame frame = frame();
        for (FocusBox box : focusBoxes(frame)) {
            if (!box.focus().equals(focus)) continue;
            Panel panel = box.panel();
            double nextScroll = panel.scroll();
            if (box.bounds().y() < panel.clip().y()) nextScroll -= panel.clip().y() - box.bounds().y();
            else if (box.bounds().bottom() > panel.clip().bottom()) nextScroll += box.bounds().bottom() - panel.clip().bottom();
            scroll.put(panel.category().id(), clamp(nextScroll, 0, panel.maxScroll()));
            if (panel.header().x() < frame.viewport().x()) horizontalScroll -= frame.viewport().x() - panel.header().x();
            else if (panel.header().right() > frame.viewport().right()) horizontalScroll += panel.header().right() - frame.viewport().right();
            horizontalScroll = clamp(horizontalScroll, 0, frame.maxHorizontalScroll());
            return;
        }
    }

    private List<FocusBox> focusBoxes(Frame frame) {
        List<FocusBox> result = new ArrayList<>();
        for (Panel panel : frame.panels()) for (EntryBox entry : panel.entries()) {
            result.add(new FocusBox(new Focus(entry.entry().id(), null), panel, entry.entry(), null, entry.header()));
            for (SettingBox setting : entry.settings())
                result.add(new FocusBox(new Focus(entry.entry().id(), setting.setting().id()), panel, entry.entry(),
                        setting.setting(), setting.bounds()));
        }
        return result;
    }

    private void expand(Entry entry) {
        if (entry.settings().isEmpty()) return;
        cancelDrag();
        if (!expanded.add(entry.id())) expanded.remove(entry.id());
        frame();
    }

    private String hintAt(Frame frame, double x, double y) {
        if (!frame.viewport().contains(x, y)) return "";
        for (Panel panel : frame.panels()) {
            if (!panel.clip().contains(x, y)) continue;
            for (EntryBox entry : panel.entries()) {
                if (entry.header().contains(x, y)) return entry.entry().hint();
                for (SettingBox setting : entry.settings())
                    if (setting.bounds().contains(x, y)) return setting.setting().hint();
            }
        }
        return "";
    }

    private int entryHeight(Entry entry) {
        return ROW_HEIGHT + (expanded.contains(entry.id()) && !entry.settings().isEmpty()
                ? 5 + entry.settings().stream().mapToInt(Setting::height).sum() : 0);
    }

    private int contentHeight(Category category) {
        return Math.max(0, category.entries().stream().mapToInt(entry -> entryHeight(entry) + ROW_GAP).sum() - ROW_GAP);
    }

    /** Package-visible snapshot used by tests; input always recomputes this, never stale render hitboxes. */
    Frame frame() {
        int available = Math.max(1, width - MARGIN * 2);
        int count = categories.size();
        int panelWidth = Math.max(132, Math.min(156, (available - PANEL_GAP * Math.max(0, count - 1)) / Math.max(1, count)));
        int totalWidth = count * panelWidth + Math.max(0, count - 1) * PANEL_GAP;
        int maxHorizontal = Math.max(0, totalWidth - available);
        horizontalScroll = clamp(horizontalScroll, 0, maxHorizontal);
        int baseX = MARGIN + Math.max(0, (available - totalWidth) / 2);
        int maxBody = Math.max(24, Math.min(330, height - 108));
        int tallest = categories.stream().mapToInt(category -> Math.min(maxBody, contentHeight(category) + PADDING * 2)).max().orElse(0);
        int top = Math.max(30, (height - tallest - HEADER_HEIGHT - HEADER_GAP) / 2 - 5);
        Rect viewport = new Rect(MARGIN, top - 3, available, tallest + HEADER_HEIGHT + HEADER_GAP + 9);
        List<Panel> panels = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Category category = categories.get(index);
            int x = baseX + index * (panelWidth + PANEL_GAP) - (int) Math.round(horizontalScroll);
            Rect header = new Rect(x, top, panelWidth, HEADER_HEIGHT);
            int content = contentHeight(category);
            Rect body = new Rect(x, header.bottom() + HEADER_GAP, panelWidth, Math.min(maxBody, content + PADDING * 2));
            Rect clip = body.inset(PADDING);
            int maxScroll = UiGeometry.maxScroll(content, clip.height());
            double offset = clamp(scroll.getOrDefault(category.id(), 0.0), 0, maxScroll);
            scroll.put(category.id(), offset);
            Rect track = new Rect(body.right() - 4, clip.y(), 2, clip.height());
            Rect thumb = UiGeometry.verticalThumb(track, content, clip.height(), offset);
            List<EntryBox> entries = new ArrayList<>();
            int rowY = clip.y() - (int) Math.round(offset);
            for (Entry entry : category.entries()) {
                Rect bounds = new Rect(clip.x(), rowY, clip.width(), entryHeight(entry));
                Rect row = new Rect(bounds.x(), rowY, bounds.width(), ROW_HEIGHT);
                Rect pill = new Rect(row.right() - 23, row.y() + 7, 16, 8);
                List<SettingBox> settings = new ArrayList<>();
                if (expanded.contains(entry.id())) {
                    int settingY = row.bottom() + 1;
                    for (Setting setting : entry.settings()) {
                        Rect settingBounds = new Rect(row.x() + 6, settingY, row.width() - 12, setting.height());
                        Rect sliderTrack = new Rect(settingBounds.x() + 3, settingBounds.y() + 21, settingBounds.width() - 6, 2);
                        settings.add(new SettingBox(setting, settingBounds, sliderTrack,
                                new Rect(sliderTrack.x() - 2, sliderTrack.y() - 5, sliderTrack.width() + 4, 12)));
                        settingY += setting.height();
                    }
                }
                entries.add(new EntryBox(entry, bounds, row, new Rect(row.x(), row.y(), 14, row.height()), pill, List.copyOf(settings)));
                rowY += bounds.height() + ROW_GAP;
            }
            panels.add(new Panel(category, header, body, clip, content, offset, maxScroll, track, thumb, List.copyOf(entries)));
        }
        Rect horizontalTrack = new Rect(MARGIN, height - 31, available, 4);
        Rect transposed = new Rect(0, horizontalTrack.x(), 1, horizontalTrack.width());
        Rect thumb = UiGeometry.verticalThumb(transposed, totalWidth, available, horizontalScroll);
        Rect horizontalThumb = new Rect(thumb.y(), horizontalTrack.y(), thumb.height(), horizontalTrack.height());
        return new Frame(viewport, List.copyOf(panels), maxHorizontal, horizontalTrack, horizontalThumb,
                baseX, top - 18, new Rect(baseX + Math.min(totalWidth, available) - 15, top - 21, 15, 15));
    }

    record Frame(Rect viewport, List<Panel> panels, int maxHorizontalScroll, Rect horizontalTrack,
                 Rect horizontalThumb, int titleX, int titleY, Rect close) { }
    record Panel(Category category, Rect header, Rect body, Rect clip, int contentHeight, double scroll,
                 int maxScroll, Rect scrollTrack, Rect scrollThumb, List<EntryBox> entries) { }
    record EntryBox(Entry entry, Rect bounds, Rect header, Rect expander, Rect pill, List<SettingBox> settings) { }
    record SettingBox(Setting setting, Rect bounds, Rect sliderTrack, Rect sliderHitBox) { }
    private record Focus(String entryId, String settingId) { }
    private record FocusBox(Focus focus, Panel panel, Entry entry, Setting setting, Rect bounds) { }
    private sealed interface Drag permits SliderDrag, ScrollDrag, HorizontalDrag { }
    private record SliderDrag(Slider setting, Rect track) implements Drag { }
    private record ScrollDrag(String category, Rect track, int thumbSize, int max, double grabOffset) implements Drag { }
    private record HorizontalDrag(Rect track, int thumbSize, int max, double grabOffset) implements Drag { }
}
