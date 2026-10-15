package dev.chunkfinder;

import dev.chunkfinder.core.BaseClues;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;

/** Native flag/classification contracts; no world, chunk meshing or server is started. */
final class ClientPresentationContractChecks {
    private static int assertions;
    static int run(Minecraft client) {
        assertions = 0;
        boolean original = client.smartCull;
        FreecamTerrainVisibility visibility = new FreecamTerrainVisibility();
        try {
            for (boolean value : new boolean[]{true, false}) {
                client.smartCull = value; visibility.begin(client);
                check(!client.smartCull, "detached camera disables native section occlusion culling");
                client.smartCull = true; visibility.enforce(client);
                check(!client.smartCull, "active freecam retains its terrain visibility setting");
                visibility.end(client);
                check(client.smartCull == value, "original culling mode is restored, including an already-disabled mode");
            }
            client.smartCull = true; visibility.begin(client); visibility.begin(client); visibility.end(client);
            check(client.smartCull, "repeated begin cannot overwrite the original culling mode");
            client.smartCull = false; visibility.begin(client); client.smartCull = true; visibility.end(client);
            check(client.smartCull, "an external culling change is not overwritten on release");
        } finally { visibility.end(client); client.smartCull = original; }

        BaseClues structure = new BaseClues();
        for (int x = 0; x < 8; x++) {
            ChunkFinderModule.observeBase(structure, Blocks.CHEST.defaultBlockState(), x, 64, 0);
            ChunkFinderModule.observeBase(structure, Blocks.FURNACE.defaultBlockState(), x, 64, 1);
            ChunkFinderModule.observeBase(structure, Blocks.TORCH.defaultBlockState(), x, 65, 0);
        }
        check(structure.results(10, true).isEmpty(), "real vanilla structure-style blocks cannot trigger strict base clues alone");
        check(!structure.results(10, false).isEmpty(), "loose activity mode is explicit, not removed");
        BaseClues base = new BaseClues();
        ChunkFinderModule.observeBase(base, Blocks.BEACON.defaultBlockState(), 7, 65, 1);
        ChunkFinderModule.observeBase(base, Blocks.CHEST.defaultBlockState(), 8, 65, 1);
        ChunkFinderModule.observeBase(base, Blocks.CRAFTING_TABLE.defaultBlockState(), 9, 65, 1);
        check(base.results(5, true).size() == 1, "native strong block plus compact supporting families creates one clue");
        check(base.results(5, true).getFirst().maxX() - base.results(5, true).getFirst().minX() == 2, "native evidence bounds are not whole-chunk bounds");
        BaseClues geode = new BaseClues();
        for (int i = 0; i < 16; i++) ChunkFinderModule.observeBase(geode, Blocks.BUDDING_AMETHYST.defaultBlockState(), i, 10, 0);
        check(geode.results(10, false).isEmpty(), "natural geodes are not classified as bases");
        ChunkFinderModule module = new ChunkFinderModule();
        check(module.strictBaseClues() && !module.showGeodes(), "real module defaults to strict clues with geodes separate and off");
        return assertions;
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
