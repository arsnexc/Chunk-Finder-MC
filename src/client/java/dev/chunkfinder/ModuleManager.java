package dev.chunkfinder;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.CompactHud;
import net.fabricmc.loader.api.FabricLoader;
import java.util.ArrayList;

public final class ModuleManager {
    private final SharedBlockReads blockReads = new SharedBlockReads();
    private int blockTurn;
    private final ReceivedBlockUpdates updates = new ReceivedBlockUpdates();
    public int pendingBlockUpdates() { return updates.pending(); }
    public long receivedBlockUpdates() { return updates.received(); }
    public long blockUpdateOverflows() { return updates.overflows(); }
    public void blockUpdated(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.core.BlockPos pos) {
        if (net.minecraft.client.Minecraft.getInstance().level != level) return;
        blockReads.changed(pos); updates.offer(level, pos);
    }
    public SharedBlockReads blockReads() { return blockReads; }
    private final RuntimeDiagnostics diagnostics = new RuntimeDiagnostics();
    public RuntimeDiagnostics diagnostics() { return diagnostics; }
    private final InterfaceConfig interfaceSettings = new InterfaceConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-ui.json"));
    public InterfaceConfig interfaceSettings() { return interfaceSettings; }
    private final ChunkFinderModule chunkFinder = new ChunkFinderModule();
    private final AncientDebrisFinderModule ancientDebrisFinder = new AncientDebrisFinderModule();
    private final ItemEspModule itemEsp = new ItemEspModule();
    private final StorageEspModule storageEsp = new StorageEspModule();
    private final SpawnerEspModule spawnerEsp = new SpawnerEspModule();
    private final EntityEspModule entityEsp = new EntityEspModule(EntityEspModule.Mode.ENTITIES);
    private final EntityEspModule droppedItemEsp = new EntityEspModule(EntityEspModule.Mode.DROPPED_ITEMS);
    private final TrajectoriesModule trajectories = new TrajectoriesModule();
    private final TargetRendererModule targetRenderer = new TargetRendererModule();
    private final FreecamModule freecam = new FreecamModule();
    private final List<Module> modules = List.of(chunkFinder, ancientDebrisFinder, itemEsp,
            storageEsp, spawnerEsp, entityEsp, droppedItemEsp, trajectories, targetRenderer, freecam);

    public ModuleManager() {
        for (BlockEspModule module : blockModules()) module.useSharedReads(blockReads);
    }
    public List<BlockEspModule> blockModules() { return List.of(itemEsp, storageEsp, spawnerEsp, ancientDebrisFinder); }
    public void tickBlocks(Minecraft client) {
        blockReads.begin(client.level);
        var blockModules = blockModules();
        var batch = updates.drain(client.level, 64);
        for (var module : blockModules) if (module.isEnabled()) {
            if (batch.overflow()) module.requestRescan();
            else for (var point : batch.points()) module.blockChanged(client.level, point.x(), point.y(), point.z());
        }
        for (int i=0; i<blockModules.size(); i++) {
            var module = blockModules.get((blockTurn+i)%blockModules.size());
            if (module.isEnabled()) module.tick(client);
        }
        blockTurn=(blockTurn+1)%blockModules.size();
    }

    public void renderHud(Minecraft client, GuiGraphics graphics) {
        if (!interfaceSettings.hud() || !FeatureAccess.allowed(client) || client.options.hideGui || client.screen != null) return;
        List<CompactHud.Row> rows = new ArrayList<>();
        if (freecam.isEnabled() && freecam.boundary() != dev.chunkfinder.core.TerrainBoundary.Status.AVAILABLE)
            rows.add(new CompactHud.Row("Terrain", freecam.boundary() == dev.chunkfinder.core.TerrainBoundary.Status.EDGE ? "EDGE" : "UNLOADED",
                    "Player-loaded data ends here; no chunk requests", 0xFFFFBF69));
        if (freecam.isEnabled()) rows.add(new CompactHud.Row("Freecam", freecam.speed() + "x", freecam.hudDetails(), 0xFF8CE0BD));
        if (chunkFinder.isEnabled()) rows.add(new CompactHud.Row(chunkFinder.strictBaseClues() ? "Base clues" : "Activity hints",
                Long.toString(chunkFinder.baseCount()), chunkFinder.hudDetails(), chunkFinder.colors().color("chunkfinder:base_clue", 0xFFFFCE8D)));
        if (chunkFinder.isEnabled() && chunkFinder.showGeodes()) rows.add(new CompactHud.Row("Geodes", Long.toString(chunkFinder.geodeCount()), "Natural budding-amethyst clusters", chunkFinder.colors().color("chunkfinder:geode", 0xFFB493FF)));
        for (BlockEspModule module : List.<BlockEspModule>of(ancientDebrisFinder, itemEsp, storageEsp, spawnerEsp))
            if (module.isEnabled()) rows.add(new CompactHud.Row(module == ancientDebrisFinder ? "Debris (unverified)" : module.name(),
                    Integer.toString(module.detectedCount()), module.hudDetails(), module.colors().color(null)));
        for (EntityEspModule module : List.of(entityEsp, droppedItemEsp))
            if (module.isEnabled()) rows.add(new CompactHud.Row(module.name(), Integer.toString(module.detectedCount()), module.targetSummary(), module.colors().color(null)));
        if (trajectories.isEnabled()) rows.add(new CompactHud.Row("Paths", Integer.toString(trajectories.visibleCount()), "Approximate client-tick preview", 0xFF80D5FF));
        if (targetRenderer.isEnabled()) rows.add(new CompactHud.Row("Target", targetRenderer.hudLabel(client), "Current crosshair target", 0xFFFAABDF));
        double scale = interfaceSettings.hudScale() / 100.0;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale((float) scale, (float) scale);
            CompactHud.draw(new MinecraftUiCanvas(graphics, client.font), (int) (graphics.guiWidth() / scale), (int) (graphics.guiHeight() / scale),
                    rows, interfaceSettings.lines(), interfaceSettings.details(), interfaceSettings.backgrounds());
        } finally { graphics.pose().popMatrix(); }
    }

    public List<Module> modules() { return modules; }
    public ChunkFinderModule chunkFinder() { return chunkFinder; }
    public AncientDebrisFinderModule ancientDebrisFinder() { return ancientDebrisFinder; }
    public ItemEspModule itemEsp() { return itemEsp; }
    public StorageEspModule storageEsp() { return storageEsp; }
    public SpawnerEspModule spawnerEsp() { return spawnerEsp; }
    public EntityEspModule entityEsp() { return entityEsp; }
    public EntityEspModule droppedItemEsp() { return droppedItemEsp; }
    public TrajectoriesModule trajectories() { return trajectories; }
    public TargetRendererModule targetRenderer() { return targetRenderer; }
    public FreecamModule freecam() { return freecam; }
}
