package dev.chunkfinder;

import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import java.util.List;

final class HudRowsScreen extends MenuScreen {
    HudRowsScreen(Screen parent,ModuleManager modules){super("HUD rows and pins",parent,modules.interfaceSettings());var settings=modules.interfaceSettings();List<Entry> entries=new ArrayList<>();
        for(var row:ModuleManager.hudDefinitions())entries.add(new Entry(row.id(),row.label(),"HUD visibility only; the module itself is not enabled or disabled",
                ()->!settings.hiddenHud().contains(row.id()),()->settings.rowVisible(row.id(),settings.hiddenHud().contains(row.id())),
                List.of(new Toggle("pin","Pin row","When active, pinned rows precede unpinned rows and survive hide-zero filtering",()->settings.pinnedHud().contains(row.id()),()->settings.rowPinned(row.id(),!settings.pinnedHud().contains(row.id()))))));
        gui=new ClickGui(List.of(new Category("rows","HUD rows",Icon.RENDER,entries)),this::onClose);
    }
}
