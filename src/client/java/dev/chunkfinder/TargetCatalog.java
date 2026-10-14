package dev.chunkfinder;

import dev.chunkfinder.core.TargetSelection.Entry;
import dev.chunkfinder.core.TargetIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Registry-backed choices. Never resolve an unknown ID through a registry's default entry. */
final class TargetCatalog {
    private TargetCatalog() { }
    static boolean selectableBlock(Block block) { return !block.defaultBlockState().isAir(); }
    static boolean storage(Block block) {
        return block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock || block instanceof HopperBlock || block instanceof DispenserBlock
                || block instanceof AbstractFurnaceBlock || block instanceof BrewingStandBlock
                || block instanceof CrafterBlock || block instanceof DecoratedPotBlock;
    }
    static boolean spawner(Block block) { return block instanceof SpawnerBlock || block instanceof TrialSpawnerBlock; }
    static List<Entry> blocks(Predicate<Block> allowed) {
        List<Entry> entries = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) if (selectableBlock(block) && allowed.test(block))
            entries.add(new Entry(BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString()));
        return List.copyOf(entries);
    }
    static List<Entry> entities() {
        List<Entry> entries = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
            entries.add(new Entry(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription().getString()));
        return List.copyOf(entries);
    }
    static List<Entry> items() {
        List<Entry> entries = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) if (item != Items.AIR)
            entries.add(new Entry(BuiltInRegistries.ITEM.getKey(item).toString(), item.getDefaultInstance().getHoverName().getString()));
        return List.copyOf(entries);
    }
    static Set<String> entityDefaults() {
        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
            if (type.getCategory() != MobCategory.MISC || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND)
                ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        return TargetIds.copy(ids);
    }
    static Set<String> itemDefaults() { return TargetIds.copy(items().stream().map(Entry::id).toList()); }
    static Set<String> storageDefaults() {
        return TargetIds.copy(blocks(block -> block instanceof ShulkerBoxBlock
                || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST
                || block == Blocks.BARREL || block == Blocks.HOPPER || block == Blocks.DISPENSER
                || block == Blocks.DROPPER).stream().map(Entry::id).toList());
    }
}
