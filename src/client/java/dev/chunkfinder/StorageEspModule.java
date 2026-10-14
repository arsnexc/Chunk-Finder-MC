package dev.chunkfinder;

/** Common container types, including optional furnace, brewing, crafter and decorated-pot targets. */
public final class StorageEspModule extends BlockEspModule {
    public StorageEspModule() {
        super("Storage ESP", "storage_esp.json", TargetCatalog.storageDefaults(),
                TargetCatalog::storage, 0xFF79E5C6, 8192);
    }
}
