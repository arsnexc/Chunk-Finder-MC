package dev.chunkfinder;

import dev.chunkfinder.core.FlightSmoothing;
import dev.chunkfinder.core.FrameSamples;
import dev.chunkfinder.core.FreecamMovement.Vector;
import dev.chunkfinder.core.TerrainBoundary;
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
        return assertions;
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-9;}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
