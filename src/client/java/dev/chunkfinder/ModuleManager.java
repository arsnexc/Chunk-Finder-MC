package dev.chunkfinder;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ModuleManager {
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

    public void renderBlockHud(Minecraft client, GuiGraphics graphics) {
        if (!FeatureAccess.allowed(client) || client.options.hideGui || client.screen != null) return;
        List<BlockEspModule> active = List.<BlockEspModule>of(ancientDebrisFinder, itemEsp, storageEsp, spawnerEsp)
                .stream().filter(Module::isEnabled).toList();
        if (active.isEmpty()) return;
        int width = Math.min(230, Math.max(120, graphics.guiWidth() / 2 - 18));
        int cardHeight = Math.min(76, Math.max(30, (graphics.guiHeight() - 24 - (active.size() - 1) * 6) / active.size()));
        int x = graphics.guiWidth() - width - 12, y = 12;
        for (BlockEspModule module : active) {
            module.renderHudAt(client, graphics, x, y, width, cardHeight);
            y += cardHeight + 6;
        }
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
