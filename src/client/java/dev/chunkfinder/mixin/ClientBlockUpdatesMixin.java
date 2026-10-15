package dev.chunkfinder.mixin;

import dev.chunkfinder.ChunkFinderClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe applied local/server block changes. No packets are sent or requested. */
@Mixin(ClientLevel.class)
public abstract class ClientBlockUpdatesMixin {
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void chunkfinder$changed(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        if(before!=after) ChunkFinderClient.blockUpdated((ClientLevel)(Object)this,pos);
    }
}
