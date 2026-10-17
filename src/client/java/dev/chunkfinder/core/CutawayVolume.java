package dev.chunkfinder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/** Small render-only window. The dirty halo touches at most eight sections per location. */
public record CutawayVolume(int x,int y,int z,int radius) {
    public CutawayVolume { radius=Math.max(1,Math.min(4,radius)); }
    public record Section(int x,int y,int z) { }
    public boolean contains(int px,int py,int pz) { return contains(px,py,pz,0); }
    public boolean contains(int px,int py,int pz,int halo) {
        long r=(long)radius+halo;
        return Math.abs((long)px-x)<=r && Math.abs((long)py-y)<=r && Math.abs((long)pz-z)<=r;
    }
    public static Set<Section> affected(CutawayVolume before,CutawayVolume after) {
        Set<Section> result=new LinkedHashSet<>();add(result,before);add(result,after);return Set.copyOf(result);
    }
    private static void add(Set<Section> result,CutawayVolume volume) {
        if(volume==null)return;long r=volume.radius+1L;
        for(int x=(int)(((long)volume.x-r)>>4);x<=(int)(((long)volume.x+r)>>4);x++)
            for(int y=(int)(((long)volume.y-r)>>4);y<=(int)(((long)volume.y+r)>>4);y++)
                for(int z=(int)(((long)volume.z-r)>>4);z<=(int)(((long)volume.z+r)>>4);z++)result.add(new Section(x,y,z));
    }
}
