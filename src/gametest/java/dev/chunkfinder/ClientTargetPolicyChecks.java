package dev.chunkfinder;

import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.TargetPolicies;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import java.util.Map;

final class ClientTargetPolicyChecks {
    static int run(){
        BlockGetter world=new BlockGetter(){
            public int getMinY(){return 0;}public int getHeight(){return 1;}
            public BlockEntity getBlockEntity(BlockPos p){return null;}
            public BlockState getBlockState(BlockPos p){return (p.getX()==15&&p.getZ()==15?Blocks.DIAMOND_ORE:Blocks.STONE).defaultBlockState();}
            public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        };
        var rules=new TargetPolicies.Snapshot(TargetPolicies.DEFAULT_RULE,Map.of("minecraft:stone",new TargetPolicies.Rule(0,0,2),"minecraft:diamond_ore",new TargetPolicies.Rule(8,0,1)));
        var probe=new BlockTargetProbe(world,p->p.getY()==0 && (p.getX()>>4)==0 && (p.getZ()>>4)==0,
                Map.of(Blocks.STONE,"minecraft:stone",Blocks.DIAMOND_ORE,"minecraft:diamond_ore"),false,null,rules);
        LoadedBlockScanner scanner=new LoadedBlockScanner(256);scanner.tick(new Object(),probe,0,0,0,0);
        var rare=new LoadedBlockScanner.Point(15,0,15);
        if(!scanner.points().contains(rare))throw new AssertionError("Native rare block was crowded out of scan cache");
        var rendered=scanner.nearestMatching(probe,.5,.5,.5,48);
        if(rendered.size()!=3 || !rendered.contains(rare))throw new AssertionError("Native per-target display limits/fairness failed");
        if(!scanner.targetId(rare).equals("minecraft:diamond_ore"))throw new AssertionError("Native target identity was lost");
        return 3;
    }
}
