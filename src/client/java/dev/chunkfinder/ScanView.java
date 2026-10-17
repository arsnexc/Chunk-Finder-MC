package dev.chunkfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Prioritize the actual view, including detached cameras, without changing the server's view distance. */
final class ScanView {
    private ScanView() { }
    static BlockPos origin(Minecraft client) {
        var camera=client.gameRenderer.getMainCamera();
        if(camera.isInitialized() && camera.entity()==client.getCameraEntity())return BlockPos.containing(camera.position());
        return client.getCameraEntity()!=null?client.getCameraEntity().blockPosition():client.player.blockPosition();
    }
}
