package dev.chunkfinder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional render-copy adapter. A changed Sodium layout disables this feature, not the game. */
public final class SodiumCutawayBridge {
    private static volatile String status="not observed";
    private record Access(Field level,Field blocks,Field light,Field x,Field y,Field z,Field volume,
                          int[] sections,int[] cells,String error) { }
    private static final ClassValue<Access> ACCESS=new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try {
                Field level=field(type,"level"),blocks=field(type,"blockArrays"),light=field(type,"lightArrays");
                Field x=field(type,"originBlockX"),y=field(type,"originBlockY"),z=field(type,"originBlockZ"),volume=field(type,"volume");
                Method section=type.getMethod("getLocalSectionIndex",int.class,int.class,int.class);
                Method cell=type.getMethod("getLocalBlockIndex",int.class,int.class,int.class);
                int[] sections=new int[27],cells=new int[4096];boolean[] usedSections=new boolean[27],usedCells=new boolean[4096];
                for(int py=0;py<3;py++)for(int pz=0;pz<3;pz++)for(int px=0;px<3;px++) {
                    int value=(int)section.invoke(null,px,py,pz);if(value<0||value>=27||usedSections[value])throw new IllegalStateException("section layout changed");
                    usedSections[value]=true;sections[(py*3+pz)*3+px]=value;
                }
                for(int py=0;py<16;py++)for(int pz=0;pz<16;pz++)for(int px=0;px<16;px++) {
                    int value=(int)cell.invoke(null,px,py,pz);if(value<0||value>=4096||usedCells[value])throw new IllegalStateException("block layout changed");
                    usedCells[value]=true;cells[(py*16+pz)*16+px]=value;
                }
                return new Access(level,blocks,light,x,y,z,volume,sections,cells,null);
            } catch(ReflectiveOperationException|RuntimeException e) {
                return new Access(null,null,null,null,null,null,null,null,null,e.getClass().getSimpleName()+": "+e.getMessage());
            }
        }
    };
    private SodiumCutawayBridge() { }
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException {
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field;
    }
    public static boolean unsupported(){return status.startsWith("unsupported");}
    public static String status(){return status;}
    public static BlockState maskState(Object slice,BlockState original,int x,int y,int z){
        FreecamCutaway.View view=viewFor(slice,x,y,z);
        return view==null?original:view.mask(original,x,y,z);
    }
    public static int brightness(Object slice,int original,LightLayer layer,int x,int y,int z){
        if(layer!=LightLayer.BLOCK)return original;
        FreecamCutaway.View view=viewFor(slice,x,y,z);
        return view!=null&&view.lit(x,y,z)?15:original;
    }
    private static FreecamCutaway.View viewFor(Object slice,int x,int y,int z){
        FreecamCutaway.View view=FreecamCutaway.peek();
        if(view==null||unsupported()||!view.volume.contains(x,y,z,1))return null;
        Access a=ACCESS.get(slice.getClass());
        if(a.error()!=null){fail(a.error());return null;}
        status="ready";
        try { return a.level.get(slice)==view.level?view:null; }
        catch(ReflectiveOperationException|RuntimeException e){fail(e.getClass().getSimpleName()+": "+e.getMessage());return null;}
    }
    public static void afterCopy(Object slice) {
        if(unsupported())return;
        Access a=ACCESS.get(slice.getClass());
        if(a.error()!=null){fail(a.error());return;}
        status="ready";
        try {
            ClientLevel level=(ClientLevel)a.level.get(slice);
            var view=FreecamCutaway.capture(level);if(view==null)return;
            BlockState[][] blocks=(BlockState[][])a.blocks.get(slice);DataLayer[][] lights=(DataLayer[][])a.light.get(slice);
            if(blocks.length!=27||lights.length!=27)throw new IllegalStateException("slice dimensions changed");
            int ox=a.x.getInt(slice),oy=a.y.getInt(slice),oz=a.z.getInt(slice);
            BoundingBox bounds=(BoundingBox)a.volume.get(slice);var v=view.volume;int r=v.radius()+1;
            int minX=Math.max(bounds.minX(),v.x()-r),maxX=Math.min(bounds.maxX(),v.x()+r);
            int minY=Math.max(bounds.minY(),v.y()-r),maxY=Math.min(bounds.maxY(),v.y()+r);
            int minZ=Math.max(bounds.minZ(),v.z()-r),maxZ=Math.min(bounds.maxZ(),v.z()+r);
            boolean[] copiedLight=new boolean[27];int masked=0,channel=LightLayer.BLOCK.ordinal();
            for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
                int dx=x-ox,dy=y-oy,dz=z-oz;
                if(dx<0||dy<0||dz<0||dx>=48||dy>=48||dz>=48)continue;
                int section=a.sections[((dy>>4)*3+(dz>>4))*3+(dx>>4)];
                int cell=a.cells[((dy&15)*16+(dz&15))*16+(dx&15)];
                BlockState[] column=blocks[section];
                // A missing neighbor is unloaded render data, not a Sodium layout change.
                if(column==null||lights[section]==null)continue;
                if(column.length!=4096||lights[section].length<=channel)throw new IllegalStateException("block array changed");
                BlockState state=column[cell];
                if(v.contains(x,y,z)&&state.isSolidRender()){column[cell]=Blocks.AIR.defaultBlockState();masked++;}
                // Clone the light layer before touching it: its original belongs to Sodium's
                // shared immutable section snapshot, not this worker's rendering view.
                if(!copiedLight[section]){
                    DataLayer source=lights[section][channel];lights[section][channel]=source==null?new DataLayer():source.copy();copiedLight[section]=true;
                }
                lights[section][channel].set(x&15,y&15,z&15,15);
            }
            view.recordMasks(masked);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e.getClass().getSimpleName()+": "+e.getMessage()); }
    }
    private static void fail(String reason) {
        if(!unsupported())ChunkFinderClient.LOGGER.warn("Inside-block cutaway disabled for this Sodium layout: {}",reason);
        status="unsupported: "+reason;
    }
}
