package dev.chunkfinder;

import java.util.Set;

/** Multiple registered block targets; legacy item_esp.json/blockId is migrated by BlockEspConfig. */
public final class ItemEspModule extends BlockEspModule {
    public ItemEspModule() {
        super("Block ESP", "item_esp.json", Set.of("minecraft:diamond_ore"),
                TargetCatalog::selectableBlock, 0xFFFFCE8D, 8192);
    }
}
