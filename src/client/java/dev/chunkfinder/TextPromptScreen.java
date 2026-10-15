package dev.chunkfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import dev.chunkfinder.ui.UiGeometry.Rect;
import java.util.function.Function;
import static dev.chunkfinder.ui.UiTheme.*;

/** Small native-metrics prompt. The callback returns an error or changes the screen on success. */
final class TextPromptScreen extends Screen {
    private final Screen parent;private final Function<String,String> submit;private EditBox input;private String error="";
    TextPromptScreen(Screen parent,String title,Function<String,String> submit){super(Component.literal(title));this.parent=parent;this.submit=submit;}
    private Rect panel(){int w=Math.min(420,width-24);return new Rect((width-w)/2,(height-138)/2,w,138);}
    private Rect cancel(){Rect p=panel();return new Rect(p.x()+12,p.bottom()-32,90,22);}
    private Rect accept(){Rect p=panel();return new Rect(p.right()-110,p.bottom()-32,98,22);}
    @Override protected void init(){String value=input==null?"":input.getValue();Rect p=panel();input=new EditBox(font,p.x()+16,p.y()+48,p.width()-32,20,Component.literal("Local file name"));input.setMaxLength(48);input.setTextColor(TEXT);input.setTextShadow(false);input.setValue(value);addRenderableWidget(input);setInitialFocus(input);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){super.renderBackground(g,x,y,delta);g.fill(0,0,width,height,0x70000000);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){var c=new MinecraftUiCanvas(g,font);Rect p=panel();outlined(c,p,8,BORDER,PANEL);text(c,getTitle().getString(),p.x()+12,p.y()+12,p.width()-24,TEXT);text(c,"Local only · letters, digits, - and _",p.x()+12,p.y()+29,p.width()-24,MUTED);text(c,error,p.x()+12,p.y()+78,p.width()-24,0xFFFF9090);outlined(c,accept(),4,FOCUS,ROW);centered(c,"Confirm",accept(),TEXT);outlined(c,cancel(),4,BORDER,ROW);centered(c,"Cancel",cancel(),TEXT);super.render(g,x,y,delta);}
    private void confirm(){try{String message=submit.apply(input.getValue());error=message==null?"":message;if(message==null&&Minecraft.getInstance().screen==this)Minecraft.getInstance().setScreen(parent);}catch(RuntimeException e){error="Could not complete: "+e.getMessage();}triggerImmediateNarration(false);}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==257||event.key()==335){confirm();return true;}return super.keyPressed(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(event.button()==0&&cancel().contains(event.x(),event.y())){onClose();return true;}if(event.button()==0&&accept().contains(event.x(),event.y())){confirm();return true;}return super.mouseClicked(event,twice);}
    @Override public Component getNarrationMessage(){return Component.literal(getTitle().getString()+". "+error);}
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
