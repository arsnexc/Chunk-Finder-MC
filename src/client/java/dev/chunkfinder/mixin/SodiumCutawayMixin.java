package dev.chunkfinder.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.chunkfinder.SodiumCutawayBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional, non-Minecraft methods: no Sodium classes or intermediary descriptors are linked into our mod. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.world.LevelSlice",remap=false)
public abstract class SodiumCutawayMixin {
    @Inject(method="copyData",at=@At("TAIL"),remap=false,require=0)
    private void chunkfinder$renderCopy(CallbackInfo ci){SodiumCutawayBridge.afterCopy(this);}

    // Production Fabric remaps Minecraft types inside Sodium descriptors to intermediary.
    // These are Sodium methods, so remap stays off and a refmap cannot translate them.
    // The Mojmap copy only matches a development workspace; require=0 skips the other one.
    @ModifyReturnValue(method="getBlockState(III)Lnet/minecraft/class_2680;",at=@At("RETURN"),remap=false,require=0)
    private BlockState chunkfinder$mask(BlockState original,int x,int y,int z){return SodiumCutawayBridge.maskState(this,original,x,y,z);}

    @ModifyReturnValue(method="getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("RETURN"),remap=false,require=0)
    private BlockState chunkfinder$maskDev(BlockState original,int x,int y,int z){return SodiumCutawayBridge.maskState(this,original,x,y,z);}

    @ModifyReturnValue(method="getBrightness(Lnet/minecraft/class_1944;Lnet/minecraft/class_2338;)I",at=@At("RETURN"),remap=false,require=0)
    private int chunkfinder$light(int original,LightLayer layer,BlockPos pos){
        return SodiumCutawayBridge.brightness(this,original,layer,pos.getX(),pos.getY(),pos.getZ());
    }

    @ModifyReturnValue(method="getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",at=@At("RETURN"),remap=false,require=0)
    private int chunkfinder$lightDev(int original,LightLayer layer,BlockPos pos){
        return SodiumCutawayBridge.brightness(this,original,layer,pos.getX(),pos.getY(),pos.getZ());
    }
}
