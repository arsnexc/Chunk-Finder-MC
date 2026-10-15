package dev.chunkfinder;

import dev.chunkfinder.core.PalettePresets;
import dev.chunkfinder.settings.PaletteFiles;
import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class PaletteToolsScreen extends MenuScreen {
    PaletteToolsScreen(EspColorScreen editor,InterfaceConfig preferences){
        super("Palette tools",editor,preferences);PaletteFiles files=new PaletteFiles(FabricLoader.getInstance().getConfigDir());
        List<Entry> tools=new ArrayList<>(),imports=new ArrayList<>();
        tools.add(action("okabe","Colorblind-friendly","Okabe-Ito colors; selected targets receive distinct slots. Other overrides are retained",()->{editor.replaceDraft(PalettePresets.apply("okabe-ito",editor.palette(),editor.selectedTargets()));Minecraft.getInstance().setScreen(editor);}));
        tools.add(action("contrast","High contrast","Bright, label-backed colors; suitability still depends on individual vision",()->{editor.replaceDraft(PalettePresets.apply("contrast",editor.palette(),editor.selectedTargets()));Minecraft.getInstance().setScreen(editor);}));
        tools.add(action("soft","Soft palette","Softer colors for selected targets",()->{editor.replaceDraft(PalettePresets.apply("soft",editor.palette(),editor.selectedTargets()));Minecraft.getInstance().setScreen(editor);}));
        tools.add(action("legend","Color legend","Effective selected/overridden target colors, including inherited values",()->Minecraft.getInstance().setScreen(new ReviewScreen(this,"Palette legend",editor.legend(),"",null))));
        tools.add(action("export","Export draft","Writes only colors to config/chunkfinder-palettes; does not Apply",()->Minecraft.getInstance().setScreen(new TextPromptScreen(this,"Export palette",name->{try{files.save(name,editor.palette());Minecraft.getInstance().setScreen(new PaletteToolsScreen(editor,preferences));return null;}catch(IOException e){return e.getMessage();}}))));
        String problem="";
        try{for(String name:files.names())imports.add(action("import-"+imports.size(),name,"Validate and preview before replacing the current draft",()->{
            try{var palette=files.load(name);List<String> preview=new ArrayList<>();preview.add("Replace the current color draft, not live module settings.");preview.add("No targets/modules are enabled or selected by this import.");preview.add("Module: "+(palette.base()==null?"automatic":dev.chunkfinder.core.EspPalette.hex(palette.base())));preview.add(palette.targets().size()+" target overrides; missing/unrelated IDs are retained.");palette.targets().forEach((id,color)->preview.add(id+" : "+dev.chunkfinder.core.EspPalette.hex(color)));
                Minecraft.getInstance().setScreen(new ReviewScreen(this,"Import "+name,preview,"Use this draft",()->{editor.replaceDraft(palette);Minecraft.getInstance().setScreen(editor);return null;}));
            }catch(IOException e){gui.notice(e.getMessage());}
        }));}catch(IOException e){problem=e.getMessage();}
        if(imports.isEmpty())imports.add(action("none","No palette files","Put a compatible JSON file in config/chunkfinder-palettes",()->gui.notice("Use Export draft, or copy a palette JSON into config/chunkfinder-palettes")));
        gui=new ClickGui(List.of(new Category("tools","Palette tools",Icon.RENDER,tools),new Category("imports","Import",Icon.PLAYER,imports)),this::onClose);
        if(!problem.isEmpty())gui.notice(problem);
    }
    private static Entry action(String id,String label,String hint,Runnable run){return new Entry(id,label,hint,()->false,run,List.of(),true);}
}
