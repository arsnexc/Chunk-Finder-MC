package dev.chunkfinder;

import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.MenuSearch;
import dev.chunkfinder.ui.PickerLayout;
import dev.chunkfinder.ui.UiCanvas;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import static dev.chunkfinder.ui.UiTheme.*;

/** Search/favorite navigation reveals a setting without toggling it or executing its action. */
final class ModuleSearchScreen extends Screen {
    private final Screen parent;
    private final ClickGui menu;
    private final InterfaceConfig preferences;
    private final MenuSearch index;
    private EditBox search;
    private List<MenuSearch.Hit> results=List.of();
    private boolean favoritesOnly,listFocus;
    private int page,selected=-1;
    ModuleSearchScreen(Screen parent,ClickGui menu,InterfaceConfig preferences){
        super(Component.literal("Find modules and settings"));this.parent=parent;this.menu=menu;this.preferences=preferences;index=menu.searchIndex();
    }
    private PickerLayout layout(){return PickerLayout.at(width,height);}
    @Override protected void init(){
        String query=search==null?"":search.getValue();int oldPage=page,oldSelected=selected;Rect box=layout().search();
        search=new EditBox(font,box.x()+7,box.y()+7,box.width()-32,13,Component.literal("Find a module, setting or action"));
        search.setMaxLength(128);search.setBordered(false);search.setTextColor(TEXT);search.setTextShadow(false);search.setHint(Component.literal("Names, actions, categories or help text"));
        search.setResponder(text->{results=index.find(text,preferences.favorites(),favoritesOnly);page=0;selected=-1;});
        search.setValue(query);results=index.find(query,preferences.favorites(),favoritesOnly);
        selected=oldSelected<results.size()?oldSelected:-1;page=layout().clampPage(oldPage,results.size());
        addRenderableWidget(search);setInitialFocus(search);focusList(listFocus);
    }
    private void focusList(boolean list){listFocus=list;setFocused(list?null:search);search.setFocused(!list);if(list && selected<0 && !results.isEmpty())selected=page*layout().pageSize();}
    private void update(){results=index.find(search.getValue(),preferences.favorites(),favoritesOnly);selected=results.isEmpty()?-1:Math.min(Math.max(0,selected),results.size()-1);page=layout().clampPage(page,results.size());}
    private void star(int which){
        if(which<0||which>=results.size())return;String id=results.get(which).id();preferences.toggleFavorite(id);update();
        for(int i=0;i<results.size();i++)if(results.get(i).id().equals(id)){selected=i;page=layout().pageContaining(i,results.size());break;}
    }
    private void pageBy(int delta){page=layout().clampPage(page+delta,results.size());selected=listFocus&&!results.isEmpty()?page*layout().pageSize():-1;}
    private void reveal(int which){if(which<0||which>=results.size())return;var hit=results.get(which);menu.reveal(hit.moduleId(),hit.settingId());Minecraft.getInstance().setScreen(parent);}
    @Override public Component getNarrationMessage(){return Component.literal(getTitle().getString()+". "+(selected>=0&&selected<results.size()?results.get(selected).label()+". "+results.get(selected).location()+". "+results.get(selected).hint():"Search names and help text. Tab to results. Control B toggles favorites. Control D stars a result."));}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){super.renderBackground(g,x,y,delta);g.fill(0,0,width,height,0x58000000);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){
        UiCanvas c=new MinecraftUiCanvas(g,font);var ui=layout();Rect p=ui.panel();
        shadow(c,p,10);outlined(c,p,10,BORDER,PANEL);text(c,"FIND MODULES & SETTINGS",p.x()+12,p.y()+12,p.width()-48,TEXT);
        text(c,results.size()+" results · Enter reveals · Ctrl+B favorites",p.x()+12,p.y()+27,p.width()-24,MUTED);
        centered(c,"×",ui.back(),MUTED);outlined(c,ui.search(),4,listFocus?BORDER:FOCUS,ROW);
        if(!search.getValue().isEmpty())centered(c,"×",ui.clearSearch(),MUTED);
        outlined(c,ui.selectedOnly(),4,favoritesOnly?FOCUS:BORDER,ROW);centered(c,"Favorites",ui.selectedOnly(),TEXT);
        text(c,"Star / right-click / Ctrl+D to favorite",ui.selectResults().x(),ui.selectResults().y()+7,p.right()-ui.selectResults().x()-12,MUTED);
        c.pushClip(ui.list());int start=page*ui.pageSize();
        for(int i=0;i<ui.pageSize() && start+i<results.size();i++){
            var hit=results.get(start+i);Rect r=ui.row(i);boolean favorite=preferences.favorites().contains(hit.id());
            outlined(c,r,4,listFocus&&selected==start+i?FOCUS:BORDER,r.contains(x,y)?ROW_HOVER:ROW);
            text(c,hit.label(),r.x()+8,r.y()+3,r.width()-32,TEXT);text(c,hit.location(),r.x()+8,r.y()+15,r.width()-32,MUTED);
            centered(c,favorite?"*":"+",new Rect(r.right()-24,r.y(),22,r.height()),favorite?TEXT:MUTED);
        }
        if(results.isEmpty())centered(c,"No matches",ui.list(),MUTED);c.popClip();
        button(c,ui.previous(),"Previous");button(c,ui.next(),"Next");button(c,ui.cancel(),"Back");button(c,ui.apply(),"Reveal control");
        super.render(g,x,y,delta);
    }
    private void button(UiCanvas c,Rect r,String label){outlined(c,r,4,BORDER,ROW);centered(c,label,r,TEXT);}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        var ui=layout();double x=e.x(),y=e.y();if(e.button()!=0&&e.button()!=1)return false;
        if(e.button()==0){
            if(ui.back().contains(x,y)||ui.cancel().contains(x,y)){onClose();return true;}
            if(ui.clearSearch().contains(x,y)){search.setValue("");focusList(false);return true;}
            if(ui.search().contains(x,y)){focusList(false);search.mouseClicked(e,twice);setDragging(true);return true;}
            if(ui.selectedOnly().contains(x,y)){favoritesOnly=!favoritesOnly;page=0;selected=-1;update();return true;}
            if(ui.previous().contains(x,y)){pageBy(-1);return true;}
            if(ui.next().contains(x,y)){pageBy(1);return true;}
            if(ui.apply().contains(x,y)){reveal(selected);return true;}
        }
        if(ui.list().contains(x,y))for(int row=0;row<ui.pageSize();row++){
            int i=page*ui.pageSize()+row;Rect r=ui.row(row);
            if(i<results.size()&&r.contains(x,y)){selected=i;focusList(true);if(e.button()==1||x>=r.right()-24)star(i);else reveal(i);return true;}
        }
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(layout().list().contains(x,y)&&v!=0){pageBy(v>0?-1:1);return true;}return super.mouseScrolled(x,y,h,v);}
    @Override public boolean keyPressed(KeyEvent e){
        int key=e.key();boolean control=(e.modifiers()&(2|8))!=0;
        if(control&&key==70){focusList(false);return true;}
        if(control&&key==68){focusList(true);star(selected);triggerImmediateNarration(false);return true;}
        if(control&&key==66){favoritesOnly=!favoritesOnly;update();return true;}
        if(key==258){focusList(!listFocus);triggerImmediateNarration(false);return true;}
        if(key==264||key==265){boolean was=listFocus;focusList(true);if(!results.isEmpty()){selected=was?Math.max(0,Math.min(results.size()-1,selected+(key==264?1:-1))):page*layout().pageSize();page=layout().pageContaining(selected,results.size());}triggerImmediateNarration(false);return true;}
        if(listFocus&&(key==257||key==335)){reveal(selected);return true;}
        if(listFocus&&(key==266||key==267)){page=layout().clampPage(page+(key==266?-1:1),results.size());selected=page*layout().pageSize();return true;}
        return super.keyPressed(e);
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
