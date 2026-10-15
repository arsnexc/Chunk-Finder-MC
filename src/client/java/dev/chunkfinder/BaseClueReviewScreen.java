package dev.chunkfinder;

import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import java.util.List;

/** Explanations and reversible session ignores; never claims ownership or confirmed player placement. */
final class BaseClueReviewScreen extends MenuScreen {
    private final ChunkFinderModule module;
    private java.util.Map<String,String> controlsByClue=java.util.Map.of();
    BaseClueReviewScreen(Screen parent,ChunkFinderModule module,InterfaceConfig preferences){super("Base clue evidence",parent,preferences);this.module=module;build();}
    private void build(){
        List<Entry> controls=List.of(action("refresh","Refresh list","Refresh this view without restarting the scanner",()->{build();gui.reveal("refresh",null);}),
                action("restore","Show ignored clues","Clear session ignores; does not change the world",()->{module.clearIgnored();build();gui.reveal("restore",null);}));
        List<Entry> clues=new ArrayList<>();java.util.Map<String,String> links=new java.util.HashMap<>();int index=0;
        for(var clue:module.clues()){
            String id="clue-"+(index++),label=(clue.ignored()?"Ignored ":"Clue ")+index;
            links.put(clue.key(),id);
            String reason=clue.coordinates()+" · score "+clue.score()+" · "+clue.explanation()+" · heuristic, not ownership proof";
            clues.add(action(id,label,reason,()->{
                boolean changed=module.ignoreClue(clue.key(),!clue.ignored());build();gui.reveal(controlsByClue.getOrDefault(clue.key(),"refresh"),null);
                gui.notice(changed?(clue.ignored()?"Clue area restored":"Clue area ignored until world/dimension change"):"Clue changed; list refreshed");
            }));
        }
        if(clues.isEmpty())clues.add(action("none","No base clues","Only compact evidence that passes the current strict/loose criteria is listed",()->gui.notice("No qualifying evidence yet; unloaded/server-hidden data cannot be inferred")));
        controlsByClue=java.util.Map.copyOf(links);
        gui=new ClickGui(List.of(new Category("review","Evidence",Icon.WORLD,clues),new Category("tools","Review tools",Icon.PLAYER,controls)),this::onClose);
    }
    private static Entry action(String id,String label,String hint,Runnable run){return new Entry(id,label,hint,()->false,run,List.of(),true);}
}
