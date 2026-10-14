package dev.chunkfinder;

import java.util.Set;

public final class SpawnerEspModule extends BlockEspModule {
    public SpawnerEspModule() {
        super("Spawner ESP", "spawner_esp.json", Set.of("minecraft:spawner", "minecraft:trial_spawner"),
                TargetCatalog::spawner, 0xFFE5A0F5, 8192);
    }
}
