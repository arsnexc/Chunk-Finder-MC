package dev.chunkfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;

/** Exposed client-reported Nether debris by default; buried server replacements are not trustworthy. */
public final class AncientDebrisFinderModule extends BlockEspModule {
    public AncientDebrisFinderModule() {
        super("Ancient Debris Finder", "ancient_debris_finder.json", Set.of("minecraft:ancient_debris"),
                block -> block == Blocks.ANCIENT_DEBRIS, 0xFF72C9FF, 4096, true);
    }
    public boolean availableInCurrentWorld(Minecraft client) {
        return FeatureAccess.allowed(client) && supportsLevel(client.level);
    }
    @Override protected boolean supportsLevel(ClientLevel level) { return level.dimension().equals(Level.NETHER); }
    @Override protected String unavailableWorldMessage() { return "Enter the Nether"; }
}
