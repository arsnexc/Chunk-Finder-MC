package dev.chunkfinder;

import dev.chunkfinder.core.TargetPolicies;
import dev.chunkfinder.core.TargetSelection;
import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Per-target rules are a draft until Apply. Target selection itself never changes here. */
final class TargetRulesScreen extends MenuScreen {
    private final Screen parent;
    private final List<TargetSelection.Entry> choices;
    private final Consumer<TargetPolicies.Snapshot> apply;
    private TargetPolicies.Draft draft;
    private String scope="";
    TargetRulesScreen(Screen parent,String label,InterfaceConfig preferences,Collection<TargetSelection.Entry> choices,
                      TargetPolicies.Snapshot initial,Consumer<TargetPolicies.Snapshot> apply){
        super(label+" target rules",parent,preferences);this.parent=parent;this.choices=List.copyOf(choices);this.apply=apply;draft=new TargetPolicies.Draft(initial);build();
    }
    private void edit(Integer priority,Integer range,Integer limit){var r=draft.rule(scope);draft.set(scope,new TargetPolicies.Rule(priority==null?r.priority():priority,range==null?r.range():range,limit==null?r.limit():limit));}
    private void build(){
        String label=scope.isEmpty()?"Module defaults":choices.stream().filter(e->e.id().equals(scope)).map(TargetSelection.Entry::name).findFirst().orElse(scope);
        gui=new ClickGui(List.of(new Category("rules","Target rules",Icon.RENDER,List.of(new Entry("target-rule",label,"Toggle visibility; changes remain a draft",()->draft.rule(scope).limit()>0,
                ()->edit(null,null,draft.rule(scope).limit()==0?48:0),List.of(
                    new Action("scope","Choose target","Search blocks/entity types/items without altering their selection",()->scope.isEmpty()?"Module defaults":scope,()->Minecraft.getInstance().setScreen(TargetPickerScreen.scopeChoice(this,"Choose rules target","Rules only · ESP selection is unchanged",choices,draft.snapshot().targets().keySet(),scope,id->{scope=id;build();gui.reveal("target-rule","scope");Minecraft.getInstance().setScreen(this);}))),
                    new Slider("priority","Priority","Higher values receive more spare slots; distinct targets get fair presence",()->draft.rule(scope).priority(),0,10,v->edit(v,null,null),Integer::toString),
                    new Slider("range","Max distance","0 inherits module range; cannot extend loaded data or the entity module's range",()->draft.rule(scope).range(),0,512,v->edit(null,v,null),v->v==0?"Module":v+"m"),
                    new Slider("limit","Display limit","0 hides this target; module-wide limits of 48 blocks / 32 entities still apply",()->draft.rule(scope).limit(),0,48,v->edit(null,null,v),Integer::toString),
                    new Action("reset","Use defaults","Reset only this scope",null,()->draft.reset(scope)),
                    new Action("reset-all","Reset all rules","Still a draft until Apply",null,()->draft=new TargetPolicies.Draft(TargetPolicies.DEFAULT)),
                    new Action("apply","Apply rules","Save once and invalidate stale scans",null,()->{apply.accept(draft.snapshot());Minecraft.getInstance().setScreen(parent);}),
                    new Action("cancel","Cancel","Discard draft",null,this::onClose)
                ))))),this::onClose);
    }
}
