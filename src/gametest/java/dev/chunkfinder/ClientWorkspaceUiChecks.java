package dev.chunkfinder;

import dev.chunkfinder.settings.ProfileStore;
import dev.chunkfinder.ui.ColorEditorLayout;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Native workspace/profile/palette/HUD interactions, isolated from user config and saves. */
final class ClientWorkspaceUiChecks {
    private static int assertions;
    static int run(ClientGameTestContext context,Consumer<String> screenshots){
        assertions=0;ProfileStore store=new ProfileStore(FabricLoader.getInstance().getConfigDir());
        ProfileStore.Snapshot original;
        try{original=store.capture();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        ModuleManager modules=context.computeOnClient(client->new ModuleManager());
        try{
            context.runOnClient(client->{modules.interfaceSettings().menuScale(100);modules.interfaceSettings().minimized(Set.of());modules.interfaceSettings().panelOrder(java.util.List.of());});
            ModuleSettingsScreen parent=context.computeOnClient(client->new ModuleSettingsScreen(modules));
            context.setScreen(()->parent);context.waitTicks(2);
            double[] from=point(context,client->parent.gui.headerBounds("world"));
            double[] to=point(context,client->parent.gui.headerBounds("player"));
            context.getInput().setCursorPos(from[0],from[1]);context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.getInput().setCursorPos(to[0],to[1]);context.waitTick();context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTick();
            check(context.computeOnClient(client->modules.interfaceSettings().panelOrder().getLast().equals("world")),"native header dragging persists snapped order");
            modified(context,GLFW.GLFW_KEY_F,GLFW.GLFW_MOD_CONTROL);
            check(context.computeOnClient(client->client.screen instanceof ModuleSearchScreen),"Ctrl+F opens module/settings search");
            context.getInput().typeChars("block esp colors");modified(context,GLFW.GLFW_KEY_D,GLFW.GLFW_MOD_CONTROL);
            check(context.computeOnClient(client->modules.interfaceSettings().favorites().contains("blocks/colors")),"native search favorites persist setting IDs");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen==parent && parent.gui.focusedLabel().contains("ESP colors")),"search reveals the setting without invoking the action");
            screenshots.accept(context.takeScreenshot("workspace-native-search-reveal").toString());

            context.setScreen(()->new HudEditorScreen(parent,modules));context.waitTicks(2);
            int oldX=context.computeOnClient(client->modules.interfaceSettings().hudX());
            context.getInput().pressKey(GLFW.GLFW_KEY_4);
            check(context.computeOnClient(client->modules.interfaceSettings().hudX()==oldX),"HUD position remains a draft");
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->modules.interfaceSettings().hudX()==1000&&modules.interfaceSettings().hudY()==1000),"native HUD corner placement saves normalized anchors");
            context.setScreen(()->new HudRowsScreen(parent,modules));context.waitTick();
            boolean enabled=context.computeOnClient(client->modules.itemEsp().isEnabled());
            context.runOnClient(client->((HudRowsScreen)client.screen).gui.reveal("blocks",null));context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
            context.runOnClient(client->((HudRowsScreen)client.screen).gui.reveal("blocks","pin"));context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
            check(context.computeOnClient(client->modules.interfaceSettings().hiddenHud().contains("blocks")&&modules.interfaceSettings().pinnedHud().contains("blocks")),"HUD row visibility and pin controls are wired");
            check(context.computeOnClient(client->modules.itemEsp().isEnabled()==enabled),"HUD controls do not toggle the module itself");

            var palette=context.computeOnClient(client->modules.itemEsp().colors().snapshot());
            context.setScreen(()->new EspColorScreen(parent,"Block ESP",modules.itemEsp().colors(),modules.itemEsp().targetChoices(),id->modules.itemEsp().colors().fallback(),Set.of("minecraft:stone","minecraft:diamond_ore")));
            click(context,client->ColorEditorLayout.at(client.screen.width,client.screen.height).tools());
            check(context.computeOnClient(client->client.screen instanceof PaletteToolsScreen),"palette tools open from the native editor");
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);context.getInput().pressKey(GLFW.GLFW_KEY_TAB);context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof EspColorScreen && ((EspColorScreen)client.screen).palette().targets().size()>=2),"native preset edits the selected targets' draft colors");
            check(context.computeOnClient(client->modules.itemEsp().colors().snapshot().equals(palette)),"preset does not save until Apply");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.setScreen(()->new EspColorScreen(parent,"Block ESP",modules.itemEsp().colors(),modules.itemEsp().targetChoices(),id->modules.itemEsp().colors().fallback()));
            context.runOnClient(client->client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("#112233"));
            click(context,client->ColorEditorLayout.at(client.screen.width,client.screen.height).tools());
            context.runOnClient(client->((PaletteToolsScreen)client.screen).gui.reveal("export",null));context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.getInput().typeChars("native-palette");context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof PaletteToolsScreen),"palette export returns to tools without applying colors");
            context.runOnClient(client->((PaletteToolsScreen)client.screen).gui.reveal("import-0",null));context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof ReviewScreen&&modules.itemEsp().colors().snapshot().equals(palette)),"palette import previews without live changes");
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof EspColorScreen&&((EspColorScreen)client.screen).palette().base()==0xFF112233),"confirmed import replaces only the editor draft");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            check(context.computeOnClient(client->modules.itemEsp().colors().snapshot().equals(palette)),"imported draft can still be canceled");

            context.setScreen(()->new ProfilesScreen(parent,modules,false));context.waitTick();
            context.runOnClient(client->((ProfilesScreen)client.screen).gui.reveal("save",null));context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof TextPromptScreen),"save-profile opens a native name prompt");
            context.getInput().typeChars("native-ui");context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof ProfilesScreen),"saving returns to profile management");
            int savedOpacity=context.computeOnClient(client->modules.interfaceSettings().hudOpacity());
            context.runOnClient(client->modules.interfaceSettings().hudOpacity(12));
            context.runOnClient(client->((ProfilesScreen)client.screen).gui.reveal("file-0",null));context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof ReviewScreen),"profile selection previews rather than applying");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->modules.interfaceSettings().hudOpacity()==12),"profile preview defaults to Cancel");
            context.runOnClient(client->((ProfilesScreen)client.screen).gui.reveal("file-0",null));context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            check(context.computeOnClient(client->client.screen instanceof ModuleSettingsScreen && modules.interfaceSettings().hudOpacity()==savedOpacity),"confirmed profile restores settings and rebuilds live bindings");
            screenshots.accept(context.takeScreenshot("workspace-native-profile-applied").toString());
            for(int[] size:new int[][]{{1280,720},{854,480}})for(int zoom:new int[]{75,100,125,150}){
                context.getInput().resizeWindow(size[0],size[1]);
                context.runOnClient(client->{modules.interfaceSettings().menuScale(zoom);modules.interfaceSettings().minimized(Set.of());modules.interfaceSettings().panelOrder(java.util.List.of());});
                ModuleSettingsScreen zoomed=context.computeOnClient(client->new ModuleSettingsScreen(modules));context.setScreen(()->zoomed);context.waitTicks(2);
                click(context,client->{Rect r=zoomed.gui.headerBounds("world");double scale=zoomed.scale();return new Rect((int)(r.x()*scale),(int)(r.y()*scale),(int)(r.width()*scale),(int)(r.height()*scale));});
                check(context.computeOnClient(client->zoomed.gui.minimizedPanels().contains("world")),"native zoom/window combination retains header hit testing: "+zoom+" / "+size[0]);
                context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
                check(context.computeOnClient(client->zoomed.getNarrationMessage().getString().contains("expanded")),"native zoom/window combination retains keyboard focus/narration");
            }
            context.runOnClient(client->modules.interfaceSettings().menuScale(100));
            context.getInput().resizeWindow(1280,720);
            var beforeColor=context.computeOnClient(client->modules.itemEsp().colors().snapshot());
            context.setScreen(()->new EspColorScreen(parent,"Block ESP",modules.itemEsp().colors(),modules.itemEsp().targetChoices(),id->modules.itemEsp().colors().fallback()));
            context.runOnClient(client->client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("#ABCDEF"));
            context.getInput().resizeWindow(854,480);context.waitTicks(2);
            check(context.computeOnClient(client->client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().getValue().equals("#ABCDEF")),"resize preserves color draft text and native caret metrics");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            check(context.computeOnClient(client->modules.itemEsp().colors().snapshot().equals(beforeColor)),"resize does not implicitly apply a color draft");
            context.getInput().resizeWindow(1280,720);
        }finally{
            try{store.apply(original);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            context.runOnClient(client->modules.reload());
        }
        return assertions;
    }
    private static void modified(ClientGameTestContext context,int key,int modifiers){context.runOnClient(client->{client.screen.afterKeyboardAction();client.screen.keyPressed(new KeyEvent(key,0,modifiers));});context.waitTick();}
    private static double[] point(ClientGameTestContext context,Function<Minecraft,Rect> bounds){return context.computeOnClient(client->{Rect r=bounds.apply(client);return new double[]{(r.x()+r.width()/2.0)*client.getWindow().getScreenWidth()/client.screen.width,(r.y()+r.height()/2.0)*client.getWindow().getScreenHeight()/client.screen.height};});}
    private static void click(ClientGameTestContext context,Function<Minecraft,Rect> bounds){double[] p=point(context,bounds);context.getInput().setCursorPos(p[0],p[1]);context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTick();}
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
