package dev.chunkfinder;

import dev.chunkfinder.settings.ProfileStore;
import dev.chunkfinder.ui.ClickGui;
import dev.chunkfinder.ui.ClickGuiModel.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ProfilesScreen extends MenuScreen {
    private final ModuleManager modules;private final ProfileStore store;private final boolean backups;
    ProfilesScreen(Screen parent,ModuleManager modules,boolean backups){
        super(backups?"Settings backups":"Local settings profiles",parent,modules.interfaceSettings());this.modules=modules;this.backups=backups;
        store=new ProfileStore(FabricLoader.getInstance().getConfigDir());List<Entry> actions=new ArrayList<>(),files=new ArrayList<>();String problem="";
        actions.add(action("save","Save current profile","Client settings only; no server address, credentials or world saves",()->Minecraft.getInstance().setScreen(new TextPromptScreen(this,"Save local profile",name->{try{store.save(name,store.capture());Minecraft.getInstance().setScreen(new ProfilesScreen(parent,modules,false));return null;}catch(IOException e){return e.getMessage();}}))));
        actions.add(action("backups",backups?"Profiles":"Backups","Preview before restoring; existing settings are backed up first",()->Minecraft.getInstance().setScreen(new ProfilesScreen(parent,modules,!backups))));
        actions.add(action("defaults","Restore defaults","Preview resets; only this mod's client files are affected",()->preview("Restore defaults",new ProfileStore.Snapshot(Map.of()))));
        try{for(String name:store.names(backups))files.add(action("file-"+files.size(),name,"Preview changes; nothing applies on selection",()->{try{preview(name,store.load(name,backups));}catch(IOException e){gui.notice(e.getMessage());}}));}catch(IOException e){problem=e.getMessage();}
        if(files.isEmpty())files.add(action("empty","No saved files","Profiles: config/chunkfinder-profiles; backups: config/chunkfinder-backups",()->{}));
        gui=new ClickGui(List.of(new Category("manage","Manage",Icon.PLAYER,actions),new Category("files",backups?"Backups":"Profiles",Icon.WORLD,files)),this::onClose);
        if(!problem.isEmpty())gui.notice(problem);
    }
    private static Entry action(String id,String label,String hint,Runnable run){return new Entry(id,label,hint,()->false,run,List.of(),true);}
    private void preview(String name,ProfileStore.Snapshot snapshot){
        try{
            var changes=store.preview(snapshot);List<String> lines=new ArrayList<>();
            lines.add(changes.size()+" client files will change. Module enable states may change.");
            lines.add("A local backup is required before writing. Freecam is stopped on reload.");
            lines.add("Game options, server authorization and user saves are excluded.");
            for(var change:changes)lines.add(change.action()+" : "+change.file());
            if(changes.isEmpty())lines.add("No file differences.");
            Minecraft.getInstance().setScreen(new ReviewScreen(this,name,lines,"Apply and reload",()->apply(snapshot)));
        }catch(IOException e){gui.notice(e.getMessage());}
    }
    private String apply(ProfileStore.Snapshot snapshot){
        try{
            var backup=store.apply(snapshot);
            try{modules.reload();}
            catch(RuntimeException failure){
                String filename=backup.getFileName().toString();store.apply(store.load(filename.substring(0,filename.length()-5),true));modules.reload();
                ModuleSettingsScreen screen=new ModuleSettingsScreen(modules);screen.gui.notice("Profile reload failed; previous client settings restored");Minecraft.getInstance().setScreen(screen);return null;
            }
            ModuleSettingsScreen screen=new ModuleSettingsScreen(modules);screen.gui.notice("Profile applied; backup: "+backup.getFileName());Minecraft.getInstance().setScreen(screen);return null;
        }catch(IOException|RuntimeException e){return e.getMessage();}
    }
}
