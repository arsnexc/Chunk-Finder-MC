package dev.chunkfinder.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mark a small render neighborhood as important, using vanilla/Sodium's normal rebuild queue. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccess {
    @Invoker("setSectionDirty")
    void chunkfinder$markSectionDirty(int x,int y,int z,boolean important);
}
