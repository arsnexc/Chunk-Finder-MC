package dev.chunkfinder;

import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

/** Small, keyboard-accessible settings surface; uses the same dark UI instead of vanilla buttons. */
final class InterfaceSettingsScreen extends MenuScreen {
    InterfaceSettingsScreen(Screen parent, ModuleManager modules) {
        super("Interface and accessibility", parent, modules.interfaceSettings());
        InterfaceConfig settings = modules.interfaceSettings();
        gui = new ClickGui(List.of(
            new Category("hud", "HUD", Icon.RENDER, List.of(new Entry("hud-controls", "Compact HUD", "Only Chunk Finder overlays are affected", settings::hud, () -> settings.hud(!settings.hud()), List.of(
                new Toggle("background", "Backgrounds", "Off by default; text shadows provide contrast", settings::backgrounds, () -> settings.backgrounds(!settings.backgrounds())),
                new Toggle("details", "Extra details", "Add short status/coordinate lines", settings::details, () -> settings.details(!settings.details())),
                new Slider("lines", "Visible rows", "Overflow becomes one +more line", settings::lines, 2, 10, settings::lines, Integer::toString),
                new Slider("scale", "HUD size", "Independent of Minecraft's chat and scoreboard", settings::hudScale, 60, 140, settings::hudScale, n -> n + "%")
            )))),
            new Category("access", "Accessibility", Icon.PLAYER, List.of(new Entry("access-controls", "High contrast", "Brighter labels and panel boundaries", settings::contrast, () -> settings.contrast(!settings.contrast()), List.of(
                new Toggle("reduced", "Reduced motion", "Disable hover and toggle animation", settings::reducedMotion, () -> settings.reducedMotion(!settings.reducedMotion())),
                new Toggle("hints", "Tooltips", "Show contextual hover help; keyboard focus is always labelled", settings::hints, () -> settings.hints(!settings.hints())),
                new Action("zoom", "Menu zoom", "Cycle zoom; fitted to small windows. Pickers keep native text metrics", () -> settings.menuScale() + "%", () -> settings.menuScale(settings.menuScale() < 100 ? 100 : settings.menuScale() < 125 ? 125 : settings.menuScale() < 150 ? 150 : 75)),
                new Action("diagnostics", "Diagnostics", "Version, renderer, loaded chunks, queues, filters and measured frame times", null, () -> net.minecraft.client.Minecraft.getInstance().setScreen(new DiagnosticsScreen(this, modules))),
                new Action("panels", "Restore all panels", "Reopen every module category without enabling modules", null, () -> settings.minimized(java.util.Set.of()))
            ))))
        ), this::onClose);
    }
}
