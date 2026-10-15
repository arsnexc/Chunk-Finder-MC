package dev.chunkfinder;

import dev.chunkfinder.ui.ColorEditorLayout;
import dev.chunkfinder.ui.CompactHud;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Native menu/color input and production HUD painting with clearly labelled synthetic rows. */
final class ClientPresentationUiChecks {
    private static int assertions;
    static int run(ClientGameTestContext context, Consumer<String> screenshot) {
        assertions = 0;
        ModuleManager modules = context.computeOnClient(client -> new ModuleManager());
        var prefs = modules.interfaceSettings();
        var blocks = modules.itemEsp();
        var original = context.computeOnClient(client -> blocks.colors().snapshot());
        Set<String> targets = context.computeOnClient(client -> blocks.targetIds());
        context.runOnClient(client -> { prefs.minimized(Set.of()); prefs.menuScale(125); prefs.contrast(true); prefs.reducedMotion(true); });
        ModuleSettingsScreen parent = context.computeOnClient(client -> new ModuleSettingsScreen(modules));
        context.setScreen(() -> parent); context.waitTicks(2);
        click(context, client -> scaled(parent.gui.headerBounds("render"), parent.scale()));
        check(context.computeOnClient(client -> parent.gui.minimizedPanels().contains("render")), "native scaled header click minimizes its category");
        check(context.computeOnClient(client -> new ModuleManager().interfaceSettings().minimized().contains("render")), "native minimized state survives preferences reload");
        check(context.computeOnClient(client -> blocks.targetIds().equals(targets)), "category minimization does not modify ESP targets");
        context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
        check(context.computeOnClient(client -> !parent.gui.minimizedPanels().contains("render")), "native keyboard restores minimized panel");
        check(context.computeOnClient(client -> parent.getNarrationMessage().getString().contains("expanded")), "native narrator message exposes panel state");
        screenshot.accept(context.takeScreenshot("modules-native-accessible").toString());
        click(context, client -> scaled(parent.gui.preferencesBounds(), parent.scale()));
        check(context.computeOnClient(client -> client.screen instanceof InterfaceSettingsScreen), "UI/HUD toolbar opens accessibility settings");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        check(context.computeOnClient(client -> client.screen == parent), "preferences return to the existing module screen");
        context.runOnClient(client -> prefs.menuScale(100));

        openColor(context, parent, blocks);
        replaceHex(context, "#12AB34");
        check(context.computeOnClient(client -> blocks.colors().snapshot().equals(original)), "typing a color only changes the draft");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        check(context.computeOnClient(client -> blocks.colors().snapshot().equals(original)), "color Escape discards the draft");
        openColor(context, parent, blocks);
        replaceHex(context, "bad");
        click(context, client -> ColorEditorLayout.at(client.screen.width, client.screen.height).apply());
        check(context.computeOnClient(client -> client.screen instanceof EspColorScreen && blocks.colors().snapshot().equals(original)), "invalid RGB cannot apply or silently turn black");
        replaceHex(context, "#12AB34");
        click(context, client -> ColorEditorLayout.at(client.screen.width, client.screen.height).apply());
        check(context.computeOnClient(client -> blocks.colors().color("minecraft:stone") == 0xFF12AB34), "native Apply sets module color");

        openColor(context, parent, blocks);
        click(context, client -> ColorEditorLayout.at(client.screen.width, client.screen.height).scope());
        check(context.computeOnClient(client -> client.screen instanceof TargetPickerScreen), "per-block color scope uses searchable registry choices");
        context.getInput().typeChars("minecraft:diamond_ore");
        context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        check(context.computeOnClient(client -> client.screen instanceof EspColorScreen && client.screen.getNarrationMessage().getString().toLowerCase(java.util.Locale.ROOT).contains("diamond")), "choosing a color target returns to the retained draft");
        replaceHex(context, "#00CCFF");
        screenshot.accept(context.takeScreenshot("esp-colors-native").toString());
        click(context, client -> ColorEditorLayout.at(client.screen.width, client.screen.height).apply());
        check(context.computeOnClient(client -> blocks.colors().color("minecraft:diamond_ore") == 0xFF00CCFF && blocks.colors().color("minecraft:stone") == 0xFF12AB34), "specific block color overrides only its own module fallback");
        check(context.computeOnClient(client -> blocks.targetIds().equals(targets)), "color editing never adds/removes ESP targets");
        check(context.computeOnClient(client -> new ItemEspModule().colors().color("minecraft:diamond_ore") == 0xFF00CCFF), "real color file survives module reload");
        context.runOnClient(client -> { blocks.colors().apply(original); prefs.contrast(false); prefs.reducedMotion(false); prefs.minimized(Set.of()); });

        var originalRules=context.computeOnClient(client -> blocks.policies().snapshot());
        context.setScreen(() -> new TargetRulesScreen(parent,blocks.name(),prefs,blocks.targetChoices(),originalRules,blocks::setPolicies));
        for(int i=0;i<4;i++)context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
        context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
        check(context.computeOnClient(client -> blocks.policies().snapshot().equals(originalRules)), "target rule slider edits remain a draft");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        check(context.computeOnClient(client -> blocks.policies().snapshot().equals(originalRules)), "rule Escape cancels without resetting scans/settings");
        context.setScreen(() -> new TargetRulesScreen(parent,blocks.name(),prefs,blocks.targetChoices(),originalRules,blocks::setPolicies));
        for(int i=0;i<4;i++)context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
        context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
        for(int i=0;i<5;i++)context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        check(context.computeOnClient(client -> client.screen==parent && blocks.policies().snapshot().defaults().priority()==1), "keyboard reaches and applies target rules");
        check(context.computeOnClient(client -> blocks.targetIds().equals(targets)), "target rule Apply never changes selected IDs");
        context.runOnClient(client -> blocks.setPolicies(originalRules));
        context.setScreen(HudPreview::new); context.waitTicks(2);
        screenshot.accept(context.takeScreenshot("compact-hud-native-synthetic").toString());
        return assertions;
    }
    private static void openColor(ClientGameTestContext context, Screen parent, BlockEspModule module) {
        context.setScreen(() -> new EspColorScreen(parent, module.name(), module.colors(), module.targetChoices(), id -> module.colors().fallback()));
        context.waitTick();
    }
    private static void replaceHex(ClientGameTestContext context, String value) {
        click(context, client -> ColorEditorLayout.at(client.screen.width, client.screen.height).hex());
        context.runOnClient(client -> {
            client.screen.afterKeyboardAction();
            client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_A, 0, Util.getPlatform() == Util.OS.OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL));
        });
        context.getInput().typeChars(value);
        check(context.computeOnClient(client -> client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().getValue().equals(value)), "native RGB edit box receives replacement text");
    }
    private static Rect scaled(Rect r, double scale) {
        return new Rect((int) (r.x() * scale), (int) (r.y() * scale), (int) (r.width() * scale), (int) (r.height() * scale));
    }
    private static void click(ClientGameTestContext context, Function<Minecraft, Rect> bounds) {
        double[] point = context.computeOnClient(client -> {
            Rect r = bounds.apply(client);
            return new double[]{(r.x() + r.width() / 2.0) * client.getWindow().getScreenWidth() / client.screen.width,
                    (r.y() + r.height() / 2.0) * client.getWindow().getScreenHeight() / client.screen.height};
        });
        context.getInput().setCursorPos(point[0], point[1]); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }
    private static final class HudPreview extends Screen {
        HudPreview() { super(Component.literal("Compact HUD · synthetic data")); }
        @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, 0xFF283B43); }
        @Override public void render(GuiGraphics g, int x, int y, float delta) {
            CompactHud.draw(new MinecraftUiCanvas(g, font), width, height, List.of(
                    new CompactHud.Row("Freecam", "10x", "", 0xFF8CE0BD), new CompactHud.Row("Base clues", "1", "", 0xFFFFCE8D),
                    new CompactHud.Row("Block ESP", "12", "", 0xFFFFD27B), new CompactHud.Row("Storage ESP", "8", "", 0xFF8CE0BD),
                    new CompactHud.Row("Entity ESP", "5", "", 0xFF89E6B6)), 6, false, false);
            g.drawString(font, Component.literal("Production HUD painter · synthetic rows · not a world/gameplay test"), 12, height - 20, 0xFFFFFFFF, false);
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
