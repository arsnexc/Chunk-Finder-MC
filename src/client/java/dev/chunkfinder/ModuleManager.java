package dev.chunkfinder;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import dev.chunkfinder.settings.InterfaceConfig;
import dev.chunkfinder.ui.CompactHud;
import dev.chunkfinder.ui.HudRows;
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
    private InterfaceConfig interfaceSettings;
    public InterfaceConfig interfaceSettings() { return interfaceSettings; }
    private ChunkFinderModule chunkFinder;
    private AncientDebrisFinderModule ancientDebrisFinder;
    private ItemEspModule itemEsp;
    private StorageEspModule storageEsp;
    private SpawnerEspModule spawnerEsp;
    private EntityEspModule entityEsp;
    private EntityEspModule droppedItemEsp;
    private TrajectoriesModule trajectories;
    private TargetRendererModule targetRenderer;
    private FreecamModule freecam;
    private List<Module> modules;

    public ModuleManager() { initialize(); }
    private void initialize() {
        var ui=new InterfaceConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-ui.json"));
        var chunks=new ChunkFinderModule();var debris=new AncientDebrisFinderModule();var blocks=new ItemEspModule();
        var storage=new StorageEspModule();var spawners=new SpawnerEspModule();var entities=new EntityEspModule(EntityEspModule.Mode.ENTITIES);
        var items=new EntityEspModule(EntityEspModule.Mode.DROPPED_ITEMS);var paths=new TrajectoriesModule();var target=new TargetRendererModule();var camera=new FreecamModule();
        List<Module> next=List.of(chunks,debris,blocks,storage,spawners,entities,items,paths,target,camera);
        for(BlockEspModule module:List.<BlockEspModule>of(blocks,storage,spawners,debris))module.useSharedReads(blockReads);
        interfaceSettings=ui;chunkFinder=chunks;ancientDebrisFinder=debris;itemEsp=blocks;storageEsp=storage;spawnerEsp=spawners;
        entityEsp=entities;droppedItemEsp=items;trajectories=paths;targetRenderer=target;freecam=camera;modules=next;
    }
    /** Call on the client thread after an explicitly confirmed profile transaction. */
    public void reload() {
        freecam.setEnabled(false); // Restores camera/input; does not overwrite the newly applied files.
        updates.drain(null,0);blockReads.invalidateAll();diagnostics.reset();initialize();
    }
    public List<BlockEspModule> blockModules() { return List.of(itemEsp, storageEsp, spawnerEsp, ancientDebrisFinder); }
    public void tickBlocks(Minecraft client) {
        blockReads.begin(client.level);
        var blockModules = blockModules();
        var batch = updates.drain(client.level, 64);
        if(chunkFinder.isEnabled()){if(batch.overflow())chunkFinder.requestRescan();else chunkFinder.blocksChanged(client.level,batch.points());}
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

    public record HudDefinition(String id,String label){}
    public static List<HudDefinition> hudDefinitions(){return List.of(new HudDefinition("freecam","Freecam"),new HudDefinition("bases","Base Finder"),new HudDefinition("geodes","Geodes"),
            new HudDefinition("debris","Ancient Debris"),new HudDefinition("blocks","Block ESP"),new HudDefinition("storage","Storage ESP"),new HudDefinition("spawners","Spawner ESP"),
            new HudDefinition("entities","Entity ESP"),new HudDefinition("items","Dropped Item ESP"),new HudDefinition("paths","Trajectories"),new HudDefinition("target","Target Renderer"));}
    public List<HudRows.Item> hudItems(Minecraft client) {
        if(!FeatureAccess.allowed(client))return List.of();
        List<HudRows.Item> rows=new ArrayList<>();
        if(freecam.isEnabled() && freecam.boundary()!=dev.chunkfinder.core.TerrainBoundary.Status.AVAILABLE)
            rows.add(new HudRows.Item("terrain",new CompactHud.Row("Terrain",freecam.boundary()==dev.chunkfinder.core.TerrainBoundary.Status.EDGE?"EDGE":"UNLOADED","Player-loaded data ends here; no chunk requests",0xFFFFBF69),false,true));
        if(freecam.isEnabled())rows.add(new HudRows.Item("freecam",new CompactHud.Row("Freecam",freecam.speed()+"x",freecam.hudDetails(),0xFF8CE0BD),false,false));
        if(chunkFinder.isEnabled())rows.add(new HudRows.Item("bases",new CompactHud.Row(chunkFinder.strictBaseClues()?"Base clues":"Activity hints",Long.toString(chunkFinder.baseCount()),chunkFinder.hudDetails(),chunkFinder.colors().color("chunkfinder:base_clue",0xFFFFCE8D)),chunkFinder.baseCount()==0,false));
        if(chunkFinder.isEnabled()&&chunkFinder.showGeodes())rows.add(new HudRows.Item("geodes",new CompactHud.Row("Geodes",Long.toString(chunkFinder.geodeCount()),"Natural budding-amethyst clusters",chunkFinder.colors().color("chunkfinder:geode",0xFFB493FF)),chunkFinder.geodeCount()==0,false));
        for(BlockEspModule module:List.<BlockEspModule>of(ancientDebrisFinder,itemEsp,storageEsp,spawnerEsp))if(module.isEnabled()){
            String id=module==ancientDebrisFinder?"debris":module==itemEsp?"blocks":module==storageEsp?"storage":"spawners";
            rows.add(new HudRows.Item(id,new CompactHud.Row(module==ancientDebrisFinder?"Debris (unverified)":module.name(),Integer.toString(module.detectedCount()),module.hudDetails(),module.colors().color(null)),module.detectedCount()==0,false));
        }
        for(EntityEspModule module:List.of(entityEsp,droppedItemEsp))if(module.isEnabled())rows.add(new HudRows.Item(module==entityEsp?"entities":"items",new CompactHud.Row(module.name(),Integer.toString(module.detectedCount()),module.targetSummary(),module.colors().color(null)),module.detectedCount()==0,false));
        if(trajectories.isEnabled())rows.add(new HudRows.Item("paths",new CompactHud.Row("Paths",Integer.toString(trajectories.visibleCount()),"Approximate client-tick preview",0xFF80D5FF),trajectories.visibleCount()==0,false));
        if(targetRenderer.isEnabled()){String label=targetRenderer.hudLabel(client);rows.add(new HudRows.Item("target",new CompactHud.Row("Target",label,"Current crosshair target",0xFFFAABDF),label.equals("No target"),false));}
        return List.copyOf(rows);
    }
    public List<CompactHud.Row> hudRows(Minecraft client){return HudRows.select(hudItems(client),interfaceSettings.hiddenHud(),interfaceSettings.pinnedHud(),interfaceSettings.hideZeroRows());}
    public void renderHud(Minecraft client,GuiGraphics graphics){
        if(!interfaceSettings.hud()||!FeatureAccess.allowed(client)||client.options.hideGui||client.screen!=null)return;
        double scale=interfaceSettings.hudScale()/100.0;graphics.pose().pushMatrix();
        try{graphics.pose().scale((float)scale,(float)scale);
            CompactHud.draw(new MinecraftUiCanvas(graphics,client.font),(int)(graphics.guiWidth()/scale),(int)(graphics.guiHeight()/scale),hudRows(client),
                    interfaceSettings.lines(),interfaceSettings.details(),interfaceSettings.backgrounds(),interfaceSettings.hudOpacity(),interfaceSettings.hudX(),interfaceSettings.hudY());
        }finally{graphics.pose().popMatrix();}
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
