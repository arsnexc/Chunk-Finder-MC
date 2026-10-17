package dev.chunkfinder;

import dev.chunkfinder.core.CutawayVolume;
import dev.chunkfinder.mixin.LevelRendererAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.concurrent.atomic.LongAdder;

/** Render-copy-only opening inside solid terrain. Never writes ClientLevel/server block or light data. */
public final class FreecamCutaway {
    public static final class View {
        public final ClientLevel level;
        public final CutawayVolume volume;
        private final Object owner;
        private final LongAdder masks=new LongAdder();
        private View(Object owner,ClientLevel level,CutawayVolume volume){this.owner=owner;this.level=level;this.volume=volume;}
        public BlockState mask(BlockState original,int x,int y,int z){
            if(volume.contains(x,y,z)&&original.isSolidRender()){masks.increment();return Blocks.AIR.defaultBlockState();}return original;
        }
        public boolean lit(int x,int y,int z){return volume.contains(x,y,z,1);}
        public void recordMasks(long count){masks.add(count);}
        public long masks(){return masks.sum();}
    }
    private static volatile View current;
    private final Object owner=new Object();
    private long tick,lastMove,lastRefresh;
    private int dirtySections;
    private boolean inside;
    private String status="Off";
    static View peek(){return current;}
    public static View capture(Level level){View v=current;return v!=null&&v.level==level?v:null;}
    public boolean active(){View v=current;return v!=null&&v.owner==owner&&hookReady();}
    public String status(){
        if(inside && FabricLoader.getInstance().isModLoaded("sodium") && !SodiumCutawayBridge.status().equals("ready"))
            return "Sodium cutaway: "+SodiumCutawayBridge.status();
        return status;
    }
    public boolean inside(){return inside;}
    public int dirtySections(){return dirtySections;}
    public long masks(){View v=current;return v!=null&&v.owner==owner?v.masks():0;}
    private static boolean hookReady(){
        return !FabricLoader.getInstance().isModLoaded("sodium") || SodiumCutawayBridge.status().equals("ready");
    }

    void update(Minecraft client,Entity camera,boolean enabled){
        tick++;
        if(client.level==null||camera==null||!LoadedChunks.at(client.level,camera.blockPosition())){inside=false;end(client);status="No loaded data";return;}
        inside=client.level.getBlockState(camera.blockPosition()).isSolidRender();
        if(!enabled||!inside){end(client);status=inside?"Inside solid blocks · cutaway off":"Normal terrain";return;}
        if(FabricLoader.getInstance().isModLoaded("sodium")&&SodiumCutawayBridge.unsupported()){
            end(client);status="Cutaway unsupported by this renderer";return;
        }
        CutawayVolume volume=new CutawayVolume(camera.getBlockX(),camera.getBlockY(),camera.getBlockZ(),3);
        View old=current;
        boolean same=old!=null&&old.owner==owner&&old.level==client.level&&old.volume.equals(volume);
        boolean enclosed=old!=null&&old.volume.contains(camera.getBlockX(),camera.getBlockY(),camera.getBlockZ());
        boolean moved=!same||!enclosed;
        // Keep one view object so worker threads and the mask counter stay stable.
        // A dirty mark can be ignored until Sodium has a built section, so retry until a rebuild is observed.
        if(!moved && masks()>0 && tick-lastRefresh<40){status="Cutaway · visual only";return;}
        if(!moved && tick-lastRefresh<5){status="Cutaway · visual only";return;}
        if(!same) current=new View(owner,client.level,volume);
        if(moved) lastMove=tick;
        lastRefresh=tick; status="Cutaway · visual only";
        dirtySections=refresh(client,old,current);
    }
    void stop(Minecraft client){end(client);inside=false;status="Off";}
    void refresh(Minecraft client){View view=current;if(view!=null&&view.owner==owner){lastRefresh=tick;dirtySections=refresh(client,view,view);}}
    void end(Minecraft client){
        View old=current;
        if(old!=null&&old.owner==owner){current=null;dirtySections=refresh(client,old,null);}
    }
    private static int refresh(Minecraft client,View old,View next){
        if(client==null||client.level==null||client.levelRenderer==null)return 0;
        CutawayVolume before=old!=null&&old.level==client.level?old.volume:null;
        CutawayVolume after=next!=null&&next.level==client.level?next.volume:null;
        int min=client.level.getMinY()>>4,max=(ScanHeight.maxYExclusive(client.level)-1)>>4,count=0;
        try {
            for(var section:CutawayVolume.affected(before,after)){
                if(section.y()<min||section.y()>max||!LoadedChunks.at(client.level,section.x(),section.z()))continue;
                ((LevelRendererAccess)client.levelRenderer).chunkfinder$markSectionDirty(section.x(),section.y(),section.z(),true);
                count++;
            }
            if(count>0)client.levelRenderer.needsUpdate();
        } catch (RuntimeException | AssertionError failure) {
            ChunkFinderClient.LOGGER.debug("Inside-block view deferred until the renderer is ready: {}", failure.toString());
            return 0;
        }
        return count;
    }
}
