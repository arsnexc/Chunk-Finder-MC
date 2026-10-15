package dev.chunkfinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chunkfinder.settings.BlockEspConfig;
import dev.chunkfinder.settings.PaletteConfig;
import dev.chunkfinder.settings.TargetPolicyConfig;
import dev.chunkfinder.core.TargetPolicies;
import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.LoadedBlockScanner.Point;
import dev.chunkfinder.core.TargetSelection.Entry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Shared loaded-block ESP lifecycle; no scans, config state, or network requests on render callbacks. */
public abstract class BlockEspModule extends Module {
    private static final int MAX_RENDERED_BLOCKS = 48;
    private final BlockEspConfig settings;
    private final Predicate<Block> allowed;
    private final PaletteConfig colors;
    private final TargetPolicyConfig policies;
    private java.util.Map<Block,String> targetNames=java.util.Map.of();
    private java.util.Map<Point, String> nearestIds = java.util.Map.of();
    private final LoadedBlockScanner scanner;
    private SharedBlockReads sharedReads;
    void useSharedReads(SharedBlockReads reads) { sharedReads = reads; }
    void blockChanged(ClientLevel level, int x, int y, int z) { if (scanner.belongsTo(level)) scanner.blockChanged(x,y,z); }
    public int pendingUpdates() { return scanner.pendingUpdates(); }
    private Set<Block> targetBlocks = Set.of();
    private List<Point> nearest = List.of();
    private String scanStatus = "Waiting for world";

    protected BlockEspModule(String name, String file, Collection<String> defaults, Predicate<Block> allowed, int color, int budget) {
        this(name, file, defaults, allowed, color, budget, false);
    }
    protected BlockEspModule(String name, String file, Collection<String> defaults, Predicate<Block> allowed, int color, int budget, boolean defaultExposedOnly) {
        super(name, ModuleCategory.VISUALS, false);
        this.allowed = allowed;
        policies=new TargetPolicyConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-rules-"+file));
        colors = new PaletteConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-colors-" + file), color);
        settings = new BlockEspConfig(FabricLoader.getInstance().getConfigDir().resolve(file), defaults, defaultExposedOnly);
        scanner = new LoadedBlockScanner(budget);
        resolveTargets();
        super.setEnabled(settings.enabled());
    }

    @Override public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) return;
        super.setEnabled(enabled); settings.enabled(enabled);
        clear(enabled ? "Scan queued" : "Module is off");
    }
    public TargetPolicyConfig policies(){return policies;}
    public void setPolicies(TargetPolicies.Snapshot value){if(policies.snapshot().equals(value))return;policies.apply(value);requestRescan();}
    public PaletteConfig colors() { return colors; }
    public String hudDetails() {
        if (nearest.isEmpty()) return scanStatus;
        Point p = nearest.getFirst();
        return p.x() + ", " + p.y() + ", " + p.z() + (exposedOnly() ? " · exposed" : " · client data");
    }
    public boolean tracesEnabled() { return settings.traces(); }
    public boolean blockHighlightEnabled() { return settings.outlines(); }
    public int searchRadius() { return settings.radius(); }
    public boolean exposedOnly() { return settings.exposedOnly(); }
    public void setExposedOnly(boolean value) {
        if (value == settings.exposedOnly()) return;
        settings.exposedOnly(value); clear("Exposure filter changed; scan queued");
    }
    public long displayedCount(String id) { return nearestIds.values().stream().filter(id::equals).count(); }
    public int detectedCount() { return scanner.points().size(); }
    public int scannedChunkCount() { return scanner.scannedCount(); }
    public int loadedChunkCount() { return scanner.loadedCount(); }
    public int pendingChunkCount() { return scanner.pendingCount(); }
    public String scanStatus() { return scanStatus; }
    public Set<String> targetIds() { return settings.targets(); }
    public List<Entry> targetChoices() { return TargetCatalog.blocks(allowed); }
    public String targetSummary() {
        if (targetIds().isEmpty()) return "None selected";
        int unavailable = targetIds().size() - targetBlocks.size();
        return targetIds().size() + " selected" + (unavailable > 0 ? " · " + unavailable + " unavailable" : "");
    }
    public void setTargetIds(Collection<String> ids) {
        if (!settings.targets(ids)) return;
        resolveTargets();
        clear(targetBlocks.isEmpty() ? "No available targets selected" : "Targets changed; scan queued");
    }
    public void setTracesEnabled(boolean enabled) { settings.traces(enabled); }
    public void setBlockHighlightEnabled(boolean enabled) { settings.outlines(enabled); }
    public void setSearchRadius(int radius) {
        int before = settings.radius(); settings.radius(radius);
        if (before != settings.radius()) clear("Radius changed; scan queued");
    }
    public void requestRescan() { clear(isEnabled() ? "Rescan queued" : "Enable module first"); }
    protected boolean supportsLevel(ClientLevel level) { return true; }
    protected String unavailableWorldMessage() { return "Waiting for world"; }

    private void resolveTargets() {
        Set<Block> resolved = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) if (TargetCatalog.selectableBlock(block) && allowed.test(block)
                && settings.targets().contains(BuiltInRegistries.BLOCK.getKey(block).toString())) resolved.add(block);
        targetBlocks = Set.copyOf(resolved);
        java.util.Map<Block,String> names=new java.util.HashMap<>();for(Block block:resolved)names.put(block,BuiltInRegistries.BLOCK.getKey(block).toString());targetNames=java.util.Map.copyOf(names);
    }
    private void clear(String status) { scanner.clear(); nearest = List.of(); nearestIds = java.util.Map.of(); scanStatus = status; }

    public void tick(Minecraft client) {
        if (!isEnabled()) return;
        if (!FeatureAccess.allowed(client)) { clear("Waiting for world"); return; }
        ClientLevel level = client.level;
        if (!supportsLevel(level)) { clear(unavailableWorldMessage()); return; }
        if (targetBlocks.isEmpty()) { clear("No available targets selected"); return; }
        LoadedBlockScanner.World world = new BlockTargetProbe(level, pos -> LoadedChunks.at(level, pos), targetNames, exposedOnly(), sharedReads, policies.snapshot()) {
            @Override public boolean emptySection(int x, int z, int y) {
                return loaded(x, z) && level.getChunk(x, z).getSection(level.getSectionIndex(y)).hasOnlyAir();
            }
        };
        BlockPos player = client.player.blockPosition();
        scanner.tick(level, world, player.getX(), player.getY(), player.getZ(), searchRadius());
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        // Compute nearest candidates once per client tick, not by sorting all matches every rendered frame.
        // Revalidate these visible candidates immediately, within a fixed 48-position budget.
        nearest = scanner.nearestMatching(world, camera.x, camera.y, camera.z, MAX_RENDERED_BLOCKS);
        // Identity is retained by the scanner; color lookup needs no extra block reads.
        java.util.Map<Point,String> ids=new java.util.HashMap<>();for(Point point:nearest)ids.put(point,scanner.targetId(point));
        nearestIds=java.util.Map.copyOf(ids);
        scanStatus = scanner.loadedCount() == 0 ? "Waiting for loaded chunks"
                : scanner.pendingCount() > 0 ? (exposedOnly() ? "Exposed scan · " : "Scanning · ") + scannedChunkCount() + "/" + loadedChunkCount()
                : exposedOnly() ? detectedCount() + " exposed · unverified"
                : this instanceof AncientDebrisFinderModule ? detectedCount() + " client matches · unverified"
                : detectedCount() == 0 ? "No matches · auto-refresh on" : detectedCount() + " matches · auto-refresh on";
    }

    public void renderWorld(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || (!tracesEnabled() && !blockHighlightEnabled()) || context.consumers() == null
                || !FeatureAccess.allowed(client) || !scanner.belongsTo(client.level) || !supportsLevel(client.level)) return;
        Vec3 camera = context.gameRenderer().getMainCamera().position();
        PoseStack matrices = context.matrices();
        PoseStack.Pose pose = matrices.last();
        VertexConsumer lines = context.consumers().getBuffer(
                net.minecraft.client.renderer.rendertype.ChunkFinderThroughWallLines.renderType());
        for (Point point : nearest) {
            int color = colors.color(nearestIds.get(point));
            if (blockHighlightEnabled()) ShapeRenderer.renderShape(matrices, lines,
                    Shapes.box(-0.035, -0.035, -0.035, 1.035, 1.035, 1.035),
                    point.x() - camera.x, point.y() - camera.y, point.z() - camera.z, color, 1.6F);
            if (tracesEnabled()) {
                lines.addVertex(pose, 0, 0, 0).setColor(color).setNormal(pose, 0, 1, 0).setLineWidth(2.0F);
                lines.addVertex(pose, (float) (point.x() + 0.5 - camera.x), (float) (point.y() + 0.5 - camera.y),
                                (float) (point.z() + 0.5 - camera.z))
                        .setColor(color).setNormal(pose, 0, 1, 0).setLineWidth(2.0F);
            }
        }
    }

}
