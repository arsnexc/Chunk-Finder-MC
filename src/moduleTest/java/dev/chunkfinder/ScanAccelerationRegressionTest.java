package dev.chunkfinder;

import dev.chunkfinder.core.CutawayVolume;
import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.LoadedBlockScanner.Point;
import dev.chunkfinder.core.ScanLayers;
import java.util.HashSet;
import java.util.Set;

final class ScanAccelerationRegressionTest {
    private static int assertions;
    static int run() {
        assertions=0;
        int[] layers=ScanLayers.ordered(-64,320,301);
        check(layers.length==384 && layers[0]==288,"scan starts in the closest section instead of at world bottom");
        Set<Integer> unique=new HashSet<>();for(int y:layers)unique.add(y);
        check(unique.size()==384&&unique.contains(-64)&&unique.contains(319),"near-height ordering still covers every build layer exactly once");
        int[] partial=ScanLayers.ordered(17,34,33);
        check(partial.length==17&&partial[0]==32&&java.util.Arrays.stream(partial).min().orElseThrow()==17,"partial sections and nonzero minima remain valid");
        class World implements LoadedBlockScanner.World {
            final Set<Point> targets=new HashSet<>();boolean fast=true,dense;int reads;
            public int minY(){return -64;}public int maxY(){return 320;}
            public boolean loaded(int x,int z){return x==0&&z==0;}
            public boolean matches(int x,int y,int z){reads++;return dense||targets.contains(new Point(x,y,z));}
            public boolean canSkipSection(int x,int z,int y){return fast&&!dense&&targets.stream().noneMatch(p->(p.y()>>4)==(y>>4));}
        }
        Point ore=new Point(8,300,8);World slow=new World(),fast=new World();slow.fast=false;slow.targets.add(ore);fast.targets.add(ore);
        LoadedBlockScanner baseline=new LoadedBlockScanner(8192),optimized=new LoadedBlockScanner(8192);Object identity=new Object();int oldTicks=0,newTicks=0;
        while(baseline.scannedCount()==0&&oldTicks++<40)baseline.tick(identity,slow,8,300,8,0);
        while(optimized.scannedCount()==0&&newTicks++<40)optimized.tick(identity,fast,8,300,8,0);
        check(baseline.points().equals(optimized.points())&&optimized.points().contains(ore),"palette skipping keeps identical target results");
        check(newTicks<oldTicks&&fast.reads<slow.reads/4,"sparse target scanning reduces reads and latency without increasing budgets");
        check(optimized.sectionsSkipped()==23,"all nonmatching full sections are skipped");

        World empty=new World();LoadedBlockScanner capped=new LoadedBlockScanner(8192);
        LoadedBlockScanner.World many=new LoadedBlockScanner.World(){
            public int minY(){return -64;}public int maxY(){return 320;}public boolean loaded(int x,int z){return true;}
            public boolean matches(int x,int y,int z){throw new AssertionError("nonmatching palette was probed");}
            public boolean canSkipSection(int x,int z,int y){return true;}
        };
        capped.tick(identity,many,0,64,0,12);
        check(capped.lastSectionChecks()==LoadedBlockScanner.MAX_SECTION_CHECKS_PER_TICK&&capped.pendingCount()>0,"palette work itself is bounded across large radii");
        optimized.clear();fast.targets.clear();optimized.tick(identity,fast,8,300,8,0);fast.targets.add(ore);optimized.blockChanged(8,300,8);optimized.tick(identity,fast,8,300,8,0);
        check(optimized.points().contains(ore),"received updates still find targets in previously skipped sections");

        World filled=new World();filled.dense=true;LoadedBlockScanner preview=new LoadedBlockScanner(4096);
        preview.tick(identity,filled,8,300,8,0);
        check(preview.scannedCount()==0&&preview.points().isEmpty(),"completed-scan accounting stays separate from previews");
        var shown=preview.nearestMatching(filled,8.5,300.5,8.5,48);
        check(!shown.isEmpty()&&shown.stream().allMatch(p->p.y()>=288&&p.y()<304),"current-height matches can render before the rest of a dense chunk finishes");
        check(preview.candidateCount()>0&&shown.size()<=48,"early previews preserve render caps");
        filled.dense=false;
        check(preview.nearestMatching(filled,8.5,300.5,8.5,48).isEmpty(),"partial matches are revalidated, not blindly drawn");
        preview.clear();check(preview.candidateCount()==0,"reset removes all partial preview data");

        var a=new CutawayVolume(15,15,15,3),b=new CutawayVolume(-17,-17,-17,3);
        check(a.contains(18,18,18)&&!a.contains(19,18,18),"cutaway is a small exact render-only volume");
        check(CutawayVolume.affected(null,a).size()<=8&&CutawayVolume.affected(a,b).size()<=16,"cutaway rebuild neighborhoods are bounded");
        check(new CutawayVolume(Integer.MAX_VALUE,0,0,3).contains(Integer.MAX_VALUE,0,0)&&!new CutawayVolume(Integer.MAX_VALUE,0,0,3).contains(Integer.MIN_VALUE,0,0),"cutaway coordinate differences cannot wrap");
        check(ScanLayers.fullSection(layers,0)&&!ScanLayers.fullSection(partial,0),"only complete aligned sections can be palette-skipped");

        class Wide implements LoadedBlockScanner.World {
            public int minY(){return -64;} public int maxY(){return 320;}
            public boolean loaded(int x,int z){return z==0 && (x==0||x==1);}
            public boolean matches(int x,int y,int z){return x==24&&y==300&&z==8;}
        }
        LoadedBlockScanner wide=new LoadedBlockScanner(4096); Wide spread=new Wide();
        wide.tick(identity,spread,8,300,8,1);
        check(wide.scannedCount()==0 && wide.pendingCount()>1, "view-height work rotates to the next loaded chunk before deep layers");
        wide.tick(identity,spread,8,300,8,1);
        check(wide.nearestMatching(spread,8.5,300.5,8.5,48).contains(new Point(24,300,8)), "a neighboring chunk can preview its near-height target before the first chunk finishes");
        return assertions;
    }
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
