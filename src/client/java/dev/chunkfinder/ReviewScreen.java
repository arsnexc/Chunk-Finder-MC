package dev.chunkfinder;

import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Supplier;
import static dev.chunkfinder.ui.UiTheme.*;

/** Scrollable preview/legend, with explicit confirmation and a safe Cancel default. */
final class ReviewScreen extends Screen {
    private final Screen parent;private final List<String> lines;private final Supplier<String> operation;private final String applyLabel;
    private int scroll;private boolean applyFocus,busy;private String error="";
    ReviewScreen(Screen parent,String title,List<String> lines,String applyLabel,Supplier<String> operation){super(Component.literal(title));this.parent=parent;this.lines=List.copyOf(lines);this.applyLabel=applyLabel;this.operation=operation;}
    private int visible(){return Math.max(1,(height-112)/14);}
    private Rect cancel(){return new Rect(24,height-40,100,22);}
    private Rect apply(){return new Rect(width-164,height-40,140,22);}
    private void scroll(int amount){scroll=Math.max(0,Math.min(Math.max(0,lines.size()-visible()),scroll+amount));}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){super.renderBackground(g,x,y,delta);g.fill(0,0,width,height,0x70000000);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){var c=new MinecraftUiCanvas(g,font);outlined(c,new Rect(12,12,width-24,height-24),8,BORDER,PANEL);text(c,getTitle().getString(),24,24,width-48,TEXT);
        text(c,error.isEmpty()?"Scroll / arrows / Page Up / Down · Tab buttons · Esc back":error,24,40,width-48,error.isEmpty()?MUTED:0xFFFF9090);
        for(int i=0;i<visible()&&i+scroll<lines.size();i++)text(c,lines.get(i+scroll),24,58+i*14,width-48,TEXT);
        outlined(c,cancel(),4,!applyFocus?FOCUS:BORDER,ROW);centered(c,operation==null?"Back":"Cancel",cancel(),TEXT);
        if(operation!=null){outlined(c,apply(),4,applyFocus?FOCUS:BORDER,ROW);centered(c,applyLabel,apply(),TEXT);}
    }
    private void confirm(){if(busy||operation==null)return;busy=true;try{String message=operation.get();error=message==null?"":message;if(message==null&&Minecraft.getInstance().screen==this)Minecraft.getInstance().setScreen(parent);}catch(RuntimeException e){error="Operation failed: "+e.getMessage();}finally{busy=false;}triggerImmediateNarration(false);}
    @Override public boolean keyPressed(KeyEvent e){int k=e.key();if(k==258){applyFocus=operation!=null&&!applyFocus;triggerImmediateNarration(false);return true;}if(k==264||k==265||k==266||k==267){scroll(k==264?1:k==265?-1:k==266?-visible():visible());triggerImmediateNarration(false);return true;}if(k==257||k==335||k==32){if(applyFocus)confirm();else onClose();return true;}return super.keyPressed(e);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(!Double.isFinite(v)||v==0)return false;scroll(v>0?-3:3);return true;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(e.button()==0){if(cancel().contains(e.x(),e.y())){onClose();return true;}if(operation!=null&&apply().contains(e.x(),e.y())){applyFocus=true;confirm();return true;}}return super.mouseClicked(e,twice);}
    @Override public Component getNarrationMessage(){return Component.literal(getTitle().getString()+". "+(error.isEmpty()?(lines.isEmpty()?"":lines.get(Math.min(scroll,lines.size()-1))):error)+". "+(applyFocus?applyLabel:"Cancel or back"));}
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
