package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Spatial evidence only; server/custom structures can still resemble player builds. */
public final class BaseClues {
    public enum Family { STORAGE, WORK, LIGHT, TRANSPORT, SPECIAL }
    public record Cluster(double x,double y,double z,int score,int blocks,int families,
                          int minX,int minY,int minZ,int maxX,int maxY,int maxZ,String key,String explanation){}
    private record Cell(int x,int y,int z){}
    private final Map<Cell,Bucket> windows=new HashMap<>();
    public void observe(int x,int y,int z,Family family,int weight,boolean strong){observe(x,y,z,family,weight,strong,strong?"player-like blocks":family.name().toLowerCase(java.util.Locale.ROOT));}
    public void observe(int x,int y,int z,Family family,int weight,boolean strong,String label){
        if(family==null||weight<=0)return;
        for(int dx=-1;dx<=0;dx++)for(int dy=-1;dy<=0;dy++)for(int dz=-1;dz<=0;dz++)
            windows.computeIfAbsent(new Cell((x>>3)+dx,(y>>3)+dy,(z>>3)+dz),ignored->new Bucket()).add(x,y,z,family,weight,strong,label);
    }
    public List<Cluster> results(int sensitivity,boolean strict){return select(windows,sensitivity,strict,4);}
    /** Per-chunk contributions merge by world-aligned window, so boundary evidence is not lost. */
    public static final class Index {
        private final Map<Long,Map<Cell,Bucket>> chunks=new HashMap<>();
        public static long key(int x,int z){return ((long)x<<32) ^ (z&0xFFFFFFFFL);}
        public void put(int x,int z,BaseClues clues){Map<Cell,Bucket> copy=new HashMap<>();clues.windows.forEach((cell,bucket)->{Bucket b=new Bucket();b.merge(bucket);copy.put(cell,b);});chunks.put(key(x,z),Map.copyOf(copy));}
        public void remove(int x,int z){chunks.remove(key(x,z));}
        public void retain(Set<Long> keys){chunks.keySet().retainAll(keys);}
        public void clear(){chunks.clear();}
        public int chunkCount(){return chunks.size();}
        public List<Cluster> results(int sensitivity,boolean strict){Map<Cell,Bucket> merged=new HashMap<>();for(var windows:chunks.values())windows.forEach((cell,bucket)->merged.computeIfAbsent(cell,ignored->new Bucket()).merge(bucket));return select(merged,sensitivity,strict,256);}
    }
    private static List<Cluster> select(Map<Cell,Bucket> windows,int sensitivity,boolean strict,int cap){
        int threshold=Math.max(8,30-Math.max(1,Math.min(10,sensitivity))*2);List<Cluster> candidates=new ArrayList<>();
        for(var entry:windows.entrySet()){
            Bucket b=entry.getValue();if(b.score<threshold||b.count<3||Integer.bitCount(b.families)<2||strict&&b.strong==0)continue;
            candidates.add(b.snapshot(entry.getKey()));
        }
        candidates.sort(Comparator.comparingInt(Cluster::score).reversed().thenComparingInt(Cluster::minX).thenComparingInt(Cluster::minY).thenComparingInt(Cluster::minZ).thenComparing(Cluster::key));
        List<Cluster> result=new ArrayList<>();
        for(Cluster c:candidates){boolean overlap=result.stream().anyMatch(old->c.minX<=old.maxX&&c.maxX>=old.minX&&c.minY<=old.maxY&&c.maxY>=old.minY&&c.minZ<=old.maxZ&&c.maxZ>=old.minZ);if(!overlap)result.add(c);if(result.size()==cap)break;}
        return List.copyOf(result);
    }
    private static final class Bucket {
        int score,count,families,strong;long x,y,z;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        final Map<String,Integer> labels=new TreeMap<>();
        void add(int px,int py,int pz,Family family,int weight,boolean important,String label){
            score+=weight;count++;families|=1<<family.ordinal();if(important)strong++;x+=px;y+=py;z+=pz;
            minX=Math.min(minX,px);minY=Math.min(minY,py);minZ=Math.min(minZ,pz);maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);maxZ=Math.max(maxZ,pz);
            labels.merge(label,1,Integer::sum);
        }
        void merge(Bucket other){score+=other.score;count+=other.count;families|=other.families;strong+=other.strong;x+=other.x;y+=other.y;z+=other.z;
            minX=Math.min(minX,other.minX);minY=Math.min(minY,other.minY);minZ=Math.min(minZ,other.minZ);maxX=Math.max(maxX,other.maxX);maxY=Math.max(maxY,other.maxY);maxZ=Math.max(maxZ,other.maxZ);other.labels.forEach((label,amount)->labels.merge(label,amount,Integer::sum));}
        Cluster snapshot(Cell cell){String reason=labels.entrySet().stream().map(e->e.getKey()+" x"+e.getValue()).collect(java.util.stream.Collectors.joining(" + "));
            return new Cluster((double)x/count+.5,(double)y/count+.5,(double)z/count+.5,score,count,Integer.bitCount(families),minX,minY,minZ,maxX,maxY,maxZ,cell.x+":"+cell.y+":"+cell.z,reason);}
    }
}
