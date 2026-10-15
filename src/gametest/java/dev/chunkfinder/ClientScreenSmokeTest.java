package dev.chunkfinder;

import dev.chunkfinder.ui.PickerLayout;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** A separate test mod: executes native rendering/input, without starting a world/server or accepting an EULA. */
public final class ClientScreenSmokeTest implements FabricClientGameTest {
    private int assertions;
    private final java.util.ArrayList<String> screenshots = new java.util.ArrayList<>();

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.guiScale().set(2));
        context.getInput().resizeWindow(1280, 720);
        context.waitTick();

        // Exercise the real module/config path with a legacy single-block file in the isolated test run directory.
        context.runOnClient(client -> {
            try {
                Path config = FabricLoader.getInstance().getConfigDir();
                Files.createDirectories(config);
                Files.writeString(config.resolve("item_esp.json"),
                        "{\"enabled\":false,\"blockId\":\"minecraft:stone\",\"searchRadius\":6}");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        ModuleManager modules = context.computeOnClient(client -> new ModuleManager());
        ItemEspModule blocks = modules.itemEsp();
        ModuleSettingsScreen parent = context.computeOnClient(client -> new ModuleSettingsScreen(modules));
        check(context.computeOnClient(client -> blocks.targetIds().equals(Set.of("minecraft:stone"))), "real module reads legacy blockId");

        context.setScreen(() -> parent);
        context.waitTicks(2);
        fontCheck(context);
        screenshot(context, "modules-native-1280x720");

        // Search + all filtered results must edit only the draft, including across pages.
        openBlocks(context, parent, blocks);
        context.getInput().typeChars("diamond_ore");
        click(context, PickerLayout::selectResults);
        check(context.computeOnClient(client -> blocks.targetIds().equals(Set.of("minecraft:stone"))), "picking targets does not write live state");
        screenshot(context, "multi-select-draft-native");
        click(context, PickerLayout::apply);
        Set<String> three = Set.of("minecraft:stone", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore");
        check(context.computeOnClient(client -> blocks.targetIds().equals(three)), "Apply adds both diamond ore variants and retains hidden stone");
        check(context.computeOnClient(client -> client.screen == parent), "Apply returns to the retained parent screen");
        check(context.computeOnClient(client -> new ItemEspModule().targetIds().equals(three)), "multi-target state persists through module reload");

        // Clear all + Escape must not change saved/live targets.
        openBlocks(context, parent, blocks);
        click(context, PickerLayout::clearAll);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        check(context.computeOnClient(client -> blocks.targetIds().equals(three)), "Escape discards cleared draft");
        check(context.computeOnClient(client -> client.screen == parent), "Escape returns to parent");

        openBlocks(context, parent, blocks);
        click(context, PickerLayout::clearAll);
        click(context, PickerLayout::apply);
        check(context.computeOnClient(client -> blocks.targetIds().isEmpty()), "empty Apply matches nothing");
        check(context.computeOnClient(client -> new ItemEspModule().targetIds().isEmpty()), "empty selection survives module reload");

        // Native character input, caret positioning, keyboard row toggling and resize.
        openBlocks(context, parent, blocks);
        context.getInput().typeChars("minecraft:diamond_ore");
        check(context.computeOnClient(client -> search(client).getValue().equals("minecraft:diamond_ore")), "native search receives typed characters");
        clickPoint(context, ui -> new double[]{ui.search().x() + 8, ui.search().y() + 9});
        context.getInput().typeChars("x");
        check(context.computeOnClient(client -> search(client).getValue().equals("xminecraft:diamond_ore")), "native search hit-testing puts the caret at the clicked beginning");
        keyWithModifiers(context, GLFW.GLFW_KEY_A,
                Util.getPlatform() == Util.OS.OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL);
        context.getInput().typeChars("minecraft:diamond_ore");
        check(context.computeOnClient(client -> search(client).getValue().equals("minecraft:diamond_ore")), "Ctrl+A and replacement text use the native edit box");
        context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
        context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
        context.getInput().resizeWindow(854, 480);
        context.waitTick();
        check(context.computeOnClient(client -> search(client).getValue().equals("minecraft:diamond_ore")), "resize retains query");
        screenshot(context, "picker-native-854x480");
        keyWithModifiers(context, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        check(context.computeOnClient(client -> blocks.targetIds().equals(Set.of("minecraft:diamond_ore"))), "keyboard draft survives resize and Ctrl+Enter applies");

        // Selected-only removal changes visible rows; hidden selected entries must survive.
        context.runOnClient(client -> blocks.setTargetIds(three));
        openBlocks(context, parent, blocks);
        context.getInput().typeChars("diamond_ore");
        click(context, PickerLayout::selectedOnly);
        click(context, PickerLayout::clearResults);
        click(context, PickerLayout::apply);
        check(context.computeOnClient(client -> blocks.targetIds().equals(Set.of("minecraft:stone"))), "native selected-only clear preserves hidden targets");

        // Exercise real registry choices and Apply callbacks for all other ESP pickers.
        blockPicker(context, parent, modules.storageEsp(), "minecraft:barrel");
        blockPicker(context, parent, modules.spawnerEsp(), "minecraft:trial_spawner");
        entityPicker(context, parent, modules.entityEsp(), "minecraft:zombie");
        entityPicker(context, parent, modules.droppedItemEsp(), "minecraft:diamond");

        // The missing-font defect must also stay fixed after a real resource reload.
        context.getInput().resizeWindow(1280, 720);
        context.setScreen(() -> parent);
        CompletableFuture<Void> reload = context.computeOnClient(Minecraft::reloadResourcePacks);
        context.waitFor(client -> reload.isDone(), 1200);
        reload.join();
        context.waitTicks(2);
        fontCheck(context);
        screenshot(context, "modules-native-after-reload");
        context.setScreen(() -> new TitleScreen());
        writeReport();
        System.out.println("Minecraft UI smoke tests passed (" + assertions + " assertions).");
        System.out.println("Native client UI only: no world/server, multiplayer, scan performance or anti-cheat verification.");
    }

    private void blockPicker(ClientGameTestContext context, ModuleSettingsScreen parent, BlockEspModule module, String id) {
        context.setScreen(() -> new TargetPickerScreen(parent, module.name() + " targets", module.targetChoices(), module.targetIds(), module::setTargetIds));
        pickOne(context, id);
        check(context.computeOnClient(client -> module.targetIds().equals(Set.of(id))), module.name() + " saves selected block ID");
    }
    private void entityPicker(ClientGameTestContext context, ModuleSettingsScreen parent, EntityEspModule module, String id) {
        context.setScreen(() -> new TargetPickerScreen(parent, module.name() + " targets", module.targetChoices(), module.targetIds(), module::setTargetIds));
        pickOne(context, id);
        check(context.computeOnClient(client -> module.targetIds().equals(Set.of(id))), module.name() + " saves selected registry ID");
    }
    private void pickOne(ClientGameTestContext context, String id) {
        context.waitTick();
        click(context, PickerLayout::clearAll);
        click(context, PickerLayout::search);
        context.getInput().typeChars(id);
        context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
        context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
        click(context, PickerLayout::apply);
    }
    private void openBlocks(ClientGameTestContext context, ModuleSettingsScreen parent, ItemEspModule blocks) {
        context.setScreen(() -> new BlockPickerScreen(parent, blocks)); context.waitTick();
    }
    private void fontCheck(ClientGameTestContext context) {
        context.runOnClient(client -> {
            int narrow = client.font.width(MinecraftUiCanvas.component("iiiiii"));
            int wide = client.font.width(MinecraftUiCanvas.component("WWWWWW"));
            check(narrow > 0, "custom font has nonzero advances");
            check(wide > narrow * 1.5, "custom font uses distinct glyphs, not repeated missing-glyph boxes");
            check(client.font.width(MinecraftUiCanvas.component("Chunk Finder 0123456789 × …")) > wide, "native UI label metrics are available");
        });
    }
    private void screenshot(ClientGameTestContext context, String name) { screenshots.add(context.takeScreenshot(name).toString()); }
    private void keyWithModifiers(ClientGameTestContext context, int key, int modifiers) {
        // Fabric API 0.141.6's TestInput constructs KeyEvent(..., modifiers=0), even
        // when holdControl/holdShift is active. Send the real event to the native
        // screen boundary; do not weaken the assertions or change production input.
        check(context.computeOnClient(client -> {
            client.screen.afterKeyboardAction();
            return client.screen.keyPressed(new KeyEvent(key, 0, modifiers));
        }), "native screen consumes modified key " + key);
        context.waitTick();
    }
    private static EditBox search(Minecraft client) {
        return client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void click(ClientGameTestContext context, Function<PickerLayout, Rect> bounds) {
        clickPoint(context, ui -> {
            Rect rect = bounds.apply(ui); return new double[]{rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0};
        });
    }
    private static void clickPoint(ClientGameTestContext context, Function<PickerLayout, double[]> point) {
        double[] physical = context.computeOnClient(client -> {
            double[] gui = point.apply(PickerLayout.at(client.screen.width, client.screen.height));
            return new double[]{gui[0] * client.getWindow().getScreenWidth() / client.screen.width,
                    gui[1] * client.getWindow().getScreenHeight() / client.screen.height};
        });
        context.getInput().setCursorPos(physical[0], physical[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private void writeReport() {
        Path report = FabricLoader.getInstance().getGameDir().resolve("client-smoke-report.txt");
        try {
            Files.writeString(report, "Minecraft UI smoke tests passed (" + assertions + " assertions).\n"
                    + "Native font, picker input, Apply/Cancel, persistence, resize, resource reload.\n"
                    + "No world/server or anti-cheat test.\n" + String.join("\n", screenshots) + "\n");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
