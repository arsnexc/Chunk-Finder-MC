package dev.chunkfinder;

import dev.chunkfinder.core.LoadedBlockScanner.Point;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Bounded, deduplicated client-thread inbox for updates already received/applied by Minecraft. */
final class ReceivedBlockUpdates {
    record Batch(List<Point> points, boolean overflow) { }
    private ClientLevel level;
    private final LinkedHashSet<Point> pending=new LinkedHashSet<>();
    private boolean overflow;
    private long received, overflows;
    void offer(ClientLevel level, BlockPos pos) {
        if(this.level!=level){pending.clear();overflow=false;this.level=level;}
        received++;
        if(overflow)return;
        if(pending.size()>=4096){pending.clear();overflow=true;overflows++;return;}
        pending.add(new Point(pos.getX(),pos.getY(),pos.getZ()));
    }
    Batch drain(ClientLevel level,int limit){
        if(this.level!=level){pending.clear();overflow=false;this.level=level;}
        boolean reset=overflow;overflow=false;
        List<Point> points=new ArrayList<>();var iterator=pending.iterator();
        while(iterator.hasNext() && points.size()<limit){points.add(iterator.next());iterator.remove();}
        return new Batch(List.copyOf(points),reset);
    }
    int pending(){return pending.size();}
    long received(){return received;}
    long overflows(){return overflows;}
}
