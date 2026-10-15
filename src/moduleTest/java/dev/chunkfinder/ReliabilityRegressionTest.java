package dev.chunkfinder;

import dev.chunkfinder.core.FlightSmoothing;
import dev.chunkfinder.core.FrameSamples;
import dev.chunkfinder.core.FreecamMovement.Vector;
import dev.chunkfinder.core.TerrainBoundary;
import dev.chunkfinder.core.TickReadCache;
import dev.chunkfinder.core.LoadedBlockScanner;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class ReliabilityRegressionTest {
    private static int assertions;
    static int run() {
        assertions=0;
        AtomicInteger probes=new AtomicInteger();
        check(TerrainBoundary.check(-1,-1,(x,z)->{probes.incrementAndGet(); return true;})==TerrainBoundary.Status.AVAILABLE,"negative-coordinate loaded terrain is available");
        check(probes.get()==9,"terrain warning has a fixed nine-query bound");
        probes.set(0);
        check(TerrainBoundary.check(0,0,(x,z)->{probes.incrementAndGet(); return false;})==TerrainBoundary.Status.UNAVAILABLE && probes.get()==1,"missing current chunk is detected without neighborhood reads");
        check(TerrainBoundary.check(0,0,(x,z)->x>=0)==TerrainBoundary.Status.EDGE,"loaded edge is distinguished from missing current chunk");
        FlightSmoothing motion=new FlightSmoothing(); Vector right=new Vector(1,0,0), zero=new Vector(0,0,0);
        for (int i=1;i<=5;i++) check(close(motion.step(right,5,4).x(),i*.2),"acceleration reaches configured speed in bounded ticks");
        for (int i=1;i<=4;i++) check(close(motion.step(zero,5,4).x(),1-i*.25),"braking reaches exact rest without asymptotic drift");
        check(motion.step(zero,5,4).equals(zero),"braking remains stopped");
        check(motion.step(right,0,0).equals(right),"zero acceleration preserves immediate legacy movement");
        check(motion.step(zero,0,0).equals(zero),"zero braking stops immediately");
        motion.step(right,0,0);
        check(motion.step(new Vector(.2,0,0),10,10).x()<=.2+1e-9,"lower speed preset cannot retain over-speed velocity");
        motion.reset(); check(motion.step(zero,10,10).equals(zero),"lifecycle reset discards momentum");
        check(motion.step(new Vector(Double.NaN,0,0),10,10).equals(zero),"nonfinite movement fails closed");
        FrameSamples frames=new FrameSamples(5);
        for(int i=1;i<=5;i++)frames.add(i*1_000_000L);
        var first=frames.summary();
        check(first.samples()==5 && first.meanMs()==3 && first.medianMs()==3 && first.p95Ms()==5,"measured percentiles use bounded real samples");
        frames.add(6_000_000);frames.add(0);frames.add(-1);
        check(frames.summary().meanMs()==4 && frames.summary().samples()==5,"ring buffer evicts old samples and ignores invalid timing");
        frames.clear();check(frames.summary().samples()==0,"benchmark reset excludes startup samples");
        sharedCache(); updates();
        return assertions;
    }
    private static void sharedCache() {
        TickReadCache<Integer,String> cache=new TickReadCache<>(2,3); Object world=new Object(); AtomicInteger reads=new AtomicInteger();
        cache.begin(world,1);
        check(cache.get(1,()->{reads.incrementAndGet();return "old";}).equals("old"),"first module reads underlying data");
        check(cache.get(1,()->{throw new AssertionError("duplicate physical read");}).equals("old") && cache.stats().hits()==1,"second module reuses the same tick's state");
        cache.invalidate(1); check(cache.get(1,()->"new").equals("new"),"received change invalidates cached state");
        cache.get(2,()->"two"); check(!cache.canRead(1),"physical reads are globally bounded");
        check(cache.get(2,()->"wrong").equals("two"),"cache hits remain available at the read ceiling");
        cache.invalidateAll(); check(!cache.canRead(1),"invalidation cannot reset or bypass the read allowance");
        try { cache.get(3,()->"three"); throw new AssertionError("unbounded read"); } catch (IllegalStateException expected) { assertions++; }
        cache.begin(world,2); check(cache.get(1,()->"next tick").equals("next tick"),"cache cannot leak state across ticks");
        cache.get(2,()->"two"); cache.get(3,()->"three"); check(cache.stats().cached()==2,"memory ceiling is independent of read ceiling");
        cache.begin(new Object(),2); check(cache.stats().cached()==0 && cache.stats().reads()==0,"dimension identity clears data even at equal tick number");
        cache.begin(null,3);check(!cache.canRead(1),"disconnected cache cannot read a world");
    }
    private static void updates() {
        class World implements LoadedBlockScanner.World {
            final Set<LoadedBlockScanner.Point> targets=new HashSet<>(); int reads; boolean loaded=true;
            public int minY(){return 0;} public int maxY(){return 32;}
            public boolean loaded(int x,int z){return loaded && x==0 && z==0;}
            public boolean matches(int x,int y,int z){if(!loaded(x>>4,z>>4))throw new AssertionError("unloaded update probe");reads++;return targets.contains(new LoadedBlockScanner.Point(x,y,z));}
        }
        Object identity=new Object();World world=new World();LoadedBlockScanner scan=new LoadedBlockScanner(16);
        scan.tick(identity,world,8,0,8,0);
        var deep=new LoadedBlockScanner.Point(8,25,8);world.targets.add(deep);
        scan.blockChanged(9,25,8); // Neighbor exposure changed, not the target itself.
        int before=world.reads;scan.tick(identity,world,8,0,8,0);
        check(scan.points().contains(deep) && scan.scannedCount()==0,"received neighbor update discovers a deep target before the full scan reaches it");
        check(world.reads-before<=scan.maxBlockReadsPerTick(world),"update probes consume the existing slice budget");
        world.targets.clear();scan.blockChanged(deep.x(),deep.y(),deep.z());scan.tick(identity,world,8,0,8,0);
        check(scan.points().isEmpty(),"mined/closed targets disappear on the next bounded update pass");
        scan.clear();var first=new LoadedBlockScanner.Point(0,0,0);world.targets.add(first);
        scan.tick(identity,world,0,0,0,0);world.targets.clear();scan.blockChanged(0,0,0);
        for(int i=0;i<550;i++)scan.tick(identity,world,0,0,0,0);
        check(!scan.points().contains(first),"a late partial-scan completion cannot resurrect a received deletion");
        world.targets.add(deep);scan.blockChanged(deep.x(),deep.y(),deep.z());scan.tick(identity,world,8,0,8,0);
        world.loaded=false;scan.tick(identity,world,8,0,8,0);
        check(scan.points().isEmpty(),"chunk unload also removes positive update overlays");
        scan.tick(null,world,0,0,0,0);check(scan.pendingUpdates()==0,"disconnect discards queued block updates");
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-9;}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
