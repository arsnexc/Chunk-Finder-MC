package dev.chunkfinder.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.chunkfinder.FreecamCutaway;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Worker-local, consistent render view. World storage, collisions and scanners are untouched. */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionCutawayMixin implements BlockAndTintGetter {
    @Shadow @Final private Level level;
    @Unique private FreecamCutaway.View chunkfinder$view;
    @Inject(method="<init>",at=@At("RETURN"))
    private void chunkfinder$capture(CallbackInfo ci){chunkfinder$view=FreecamCutaway.capture(level);}
    @ModifyReturnValue(method="getBlockState",at=@At("RETURN"))
    private BlockState chunkfinder$visibleState(BlockState original,BlockPos pos){
        return chunkfinder$view==null?original:chunkfinder$view.mask(original,pos.getX(),pos.getY(),pos.getZ());
    }
    @Override public int getBrightness(LightLayer layer,BlockPos pos){
        if(chunkfinder$view!=null && layer==LightLayer.BLOCK && chunkfinder$view.lit(pos.getX(),pos.getY(),pos.getZ()))return 15;
        return BlockAndTintGetter.super.getBrightness(layer,pos);
    }
    @Override public int getRawBrightness(BlockPos pos,int ambientDarkness){
        if(chunkfinder$view!=null && chunkfinder$view.lit(pos.getX(),pos.getY(),pos.getZ()))return 15;
        return BlockAndTintGetter.super.getRawBrightness(pos,ambientDarkness);
    }
}
