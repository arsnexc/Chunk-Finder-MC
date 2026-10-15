package dev.chunkfinder;

import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.CompactHud;
import dev.chunkfinder.ui.HudAnchor;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import static dev.chunkfinder.ui.UiTheme.*;

/** Responsive HUD placement draft. World and module state are not altered by the editor. */
final class HudEditorScreen extends Screen {
    private final Screen parent;private final InterfaceConfig prefs;private final List<CompactHud.Row> rows;
    private int x,y;private boolean snap,dragging,saveFocus;private double grabX,grabY;private Rect box=new Rect(8,8,0,0);
    HudEditorScreen(Screen parent,ModuleManager modules){super(Component.literal("HUD layout"));this.parent=parent;prefs=modules.interfaceSettings();x=prefs.hudX();y=prefs.hudY();snap=prefs.snapHud();
        var current=modules.hudRows(Minecraft.getInstance());rows=current.isEmpty()?List.of(new CompactHud.Row("Layout preview","3","Synthetic row, not live world data",0xFF8CE0BD),new CompactHud.Row("Block ESP","12","Preview only",0xFFFFD27B)):current;
    }
    @Override protected void init(){dragging=false;}
    private double scale(){return prefs.hudScale()/100.0;}
    private Rect save(){return new Rect(width-154,height-32,130,22);}
    private Rect cancel(){return new Rect(24,height-32,100,22);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0x30000000);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){double s=scale();g.pose().pushMatrix();
        try{g.pose().scale((float)s,(float)s);var c=new MinecraftUiCanvas(g,font);box=CompactHud.draw(c,(int)(width/s),(int)(height/s),rows,prefs.lines(),prefs.details(),prefs.backgrounds(),prefs.hudOpacity(),x,y);
            outlined(c,new Rect(box.x()-2,box.y()-2,box.width()+4,box.height()+4),5,FOCUS,0x05111111);
        }finally{g.pose().popMatrix();}
        var c=new MinecraftUiCanvas(g,font);g.fill(0,height-62,width,height,0xDD151515);
        text(c,"Drag HUD · Arrows nudge · Shift = 8px · S snap: "+(snap?"on":"off")+" · 1–4 corners · Home reset",12,height-54,width-24,TEXT);
        outlined(c,cancel(),4,!saveFocus?FOCUS:BORDER,ROW);centered(c,"Cancel",cancel(),TEXT);outlined(c,save(),4,saveFocus?FOCUS:BORDER,ROW);centered(c,"Save position",save(),TEXT);
    }
    private void position(double px,double py){x=HudAnchor.normalized(px,(int)(width/scale()),box.width(),snap);y=HudAnchor.normalized(py,(int)(height/scale()),box.height(),snap);}
    private void apply(){prefs.hudPosition(x,y);prefs.snapHud(snap);Minecraft.getInstance().setScreen(parent);}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(e.button()!=0)return false;if(save().contains(e.x(),e.y())){apply();return true;}if(cancel().contains(e.x(),e.y())){onClose();return true;}
        double px=e.x()/scale(),py=e.y()/scale();if(box.contains(px,py)){dragging=true;grabX=px-box.x();grabY=py-box.y();return true;}return false;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging&&e.button()==0){position(e.x()/scale()-grabX,e.y()/scale()-grabY);return true;}return false;}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(e.button()==0&&dragging){dragging=false;return true;}return false;}
    @Override public boolean keyPressed(KeyEvent e){int key=e.key();
        if(key==258){saveFocus=!saveFocus;triggerImmediateNarration(false);return true;}
        if(key==83){snap=!snap;triggerImmediateNarration(false);return true;}
        if(key>=49&&key<=52){x=key==50||key==52?1000:0;y=key>=51?1000:0;return true;}
        if(key==268){x=y=0;return true;}
        if(key>=262&&key<=265){boolean old=snap;snap=false;int step=(e.modifiers()&1)==0?1:8;Rect current=HudAnchor.place((int)(width/scale()),(int)(height/scale()),box.width(),box.height(),x,y);position(current.x()+(key==262?step:key==263?-step:0),current.y()+(key==264?step:key==265?-step:0));snap=old;return true;}
        if(key==257||key==335){if(saveFocus)apply();else onClose();return true;}
        return super.keyPressed(e);
    }
    @Override public Component getNarrationMessage(){return Component.literal("HUD layout "+x/10+" percent across, "+y/10+" percent down. Snap "+snap+". "+(saveFocus?"Save position":"Cancel"));}
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
