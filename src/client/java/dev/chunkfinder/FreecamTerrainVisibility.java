package dev.chunkfinder;

import net.minecraft.client.Minecraft;

/** Reveals available terrain sections to the detached camera; never loads or requests chunks. */
final class FreecamTerrainVisibility {
    private Boolean previous;

    void begin(Minecraft client) {
        if (previous == null) previous = client.smartCull;
        enforce(client);
    }
    void enforce(Minecraft client) {
        if (previous != null && client.smartCull) {
            client.smartCull = false;
            refresh(client);
        }
    }
    void end(Minecraft client) {
        if (previous != null && client != null && !client.smartCull && previous) {
            client.smartCull = previous;
            refresh(client);
        }
        previous = null;
    }
    private static void refresh(Minecraft client) {
        // Invalidate the visibility graph, not all chunk meshes. The renderer's
        // normal camera repositioning compiles newly visible, already-loaded sections.
        if (client.level != null && client.levelRenderer != null) client.levelRenderer.needsUpdate();
    }
}
