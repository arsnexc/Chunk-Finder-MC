package dev.chunkfinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import dev.chunkfinder.core.ChunkRange;
import dev.chunkfinder.core.BaseClues;
import dev.chunkfinder.settings.PaletteConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import dev.chunkfinder.core.Nearest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChunkFinderModule extends Module {
    private final ChunkFinderSettings settings = ChunkFinderSettings.load();
    private final PaletteConfig colors = new PaletteConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-colors-base-finder.json"), 0xFFFFCE8D);
    public PaletteConfig colors() { return colors; }
    public int automaticColor(String id) { return "chunkfinder:geode".equals(id) ? 0xFFB493FF : 0xFFFFCE8D; }
    private final ArrayDeque<ChunkCoord> queue = new ArrayDeque<>();
    private final Map<ChunkCoord, List<Detection>> results = new HashMap<>();
    private final BaseClues.Index baseIndex=new BaseClues.Index();
    private List<Detection> allDetections=List.of();
    private final List<AABB> ignored=new ArrayList<>();
    public record Clue(String key,String coordinates,String explanation,int score,boolean ignored){}
    private boolean ignored(Detection clue){return clue.kind()==Kind.ACTIVITY && ignored.stream().anyMatch(box->box.intersects(clue.bounds()));}
    public List<Clue> clues(){return allDetections.stream().filter(d->d.kind()==Kind.ACTIVITY).map(d->new Clue(d.key(),(int)Math.floor(d.x())+", "+(int)Math.floor(d.y())+", "+(int)Math.floor(d.z()),d.explanation(),d.evidence(),ignored(d))).toList();}
    public boolean ignoreClue(String key,boolean value){
        var clue=allDetections.stream().filter(d->d.key().equals(key)&&d.kind()==Kind.ACTIVITY).findFirst();if(clue.isEmpty())return false;
        if(value){if(ignored.size()>=64)ignored.removeFirst();ignored.add(clue.get().bounds().inflate(1));}
        else ignored.removeIf(box->box.intersects(clue.get().bounds()));publishDetections();return true;
    }
    public void clearIgnored(){ignored.clear();publishDetections();}
    public void blocksChanged(ClientLevel level,List<dev.chunkfinder.core.LoadedBlockScanner.Point> changed){
        if(level!=lastLevel || !isEnabled())return;java.util.Set<ChunkCoord> chunks=new java.util.HashSet<>();
        for(var point:changed){ChunkCoord chunk=new ChunkCoord(point.x()>>4,point.z()>>4);if(ChunkRange.contains(chunk.x,chunk.z,queueX,queueZ,settings.simulationDistance()))chunks.add(chunk);}
        boolean removed=false;
        for(ChunkCoord chunk:chunks){if(results.remove(chunk)!=null)removed=true;baseIndex.remove(chunk.x,chunk.z);
            if(activeScan!=null&&activeScan.chunk.equals(chunk))activeScan.dirty=true;
            else if(LoadedChunks.at(level,chunk.x,chunk.z)){queue.remove(chunk);queue.addFirst(chunk);}}
        if(removed)publishDetections();
    }
    private List<Detection> renderDetections = List.of(), nearestDetections = List.of();
    private int baseCount, geodeCount;
    private ClientLevel lastLevel;
    private ChunkScan activeScan;
    private long tickCount;
    private long nextRefreshTick;
    private static final int BLOCKS_PER_TICK = 4096;
    private int queueX = Integer.MIN_VALUE, queueZ = Integer.MIN_VALUE, queueDistance = -1;

    public ChunkFinderModule() {
        super("Base Finder", ModuleCategory.VISUALS, false);
        super.setEnabled(settings.enabled());
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        settings.setEnabled(enabled);
        if (!enabled) {
            queue.clear();
            activeScan = null;
            results.clear();
            nextRefreshTick = 0;
            publishDetections();
        }
    }
    public void requestRescan() {
        queue.clear(); activeScan = null; results.clear(); nextRefreshTick = 0;
        queueX = queueZ = Integer.MIN_VALUE; queueDistance = -1;
        publishDetections();
    }
    public boolean strictBaseClues() { return settings.strict(); }
    public void setStrictBaseClues(boolean value) { if (value != settings.strict()) { settings.strict(value); requestRescan(); } }
    public boolean showGeodes() { return settings.geodes(); }
    public void setShowGeodes(boolean value) { if (value != settings.geodes()) { settings.geodes(value); requestRescan(); } }
    public long baseCount() { return baseCount; }
    public long geodeCount() { return geodeCount; }
    public String hudDetails() {
        return nearestDetections.stream().filter(d -> d.kind() == Kind.ACTIVITY).findFirst()
                .map(d -> (int) Math.floor(d.x()) + ", " + (int) Math.floor(d.y()) + ", " + (int) Math.floor(d.z()) + " · heuristic")
                .orElse("Scanning loaded chunks · heuristic");
    }
    public int sensitivity() { return settings.sensitivity(); }
    public int simulationDistance() { return settings.simulationDistance(); }
    public void adjustSensitivity(int n) {
        settings.setSensitivity(settings.sensitivity() + n);
        queue.clear(); activeScan = null; results.clear(); nextRefreshTick = 0; publishDetections();
    }
    public void adjustSimulationDistance(int n) {
        settings.setSimulationDistance(settings.simulationDistance() + n);
        queue.clear(); activeScan = null; results.clear(); nextRefreshTick = 0; publishDetections();
    }

    public void tick(Minecraft client) {
        if (!FeatureAccess.allowed(client)) {
            lastLevel = null; ignored.clear();
            queue.clear();
            activeScan = null;
            results.clear();
            nextRefreshTick = 0;
            publishDetections();
            queueX = Integer.MIN_VALUE;
            queueZ = Integer.MIN_VALUE;
            return;
        }
        ClientLevel level = client.level;
        tickCount++;
        if (level == null || client.player == null) return;
        if (level != lastLevel) {
            lastLevel = level; ignored.clear(); results.clear(); queue.clear(); activeScan = null;
            publishDetections();
            queueX = Integer.MIN_VALUE; queueZ = Integer.MIN_VALUE; queueDistance = -1; nextRefreshTick = 0;
        }
        int cx = client.player.blockPosition().getX() >> 4;
        int cz = client.player.blockPosition().getZ() >> 4;
        int radius = settings.simulationDistance();
        boolean pruned = results.keySet().removeIf(c -> !ChunkRange.contains(c.x(), c.z(), cx, cz, radius)
                || !LoadedChunks.at(level,c.x(),c.z()));
        if (pruned) publishDetections();
        int move = Math.max(1, radius / 2);
        if ((queue.isEmpty() && activeScan == null && tickCount >= nextRefreshTick)
                || radius != queueDistance
                || queueX == Integer.MIN_VALUE || Math.abs((long) cx - queueX) >= move || Math.abs((long) cz - queueZ) >= move) rebuild(level, cx, cz, radius);
        int budget = BLOCKS_PER_TICK;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget > 0) {
            if (activeScan == null) {
                ChunkCoord chunk = queue.pollFirst();
                if (chunk == null) break;
                BlockPos probe = new BlockPos(chunk.x() * 16 + 8, level.getMinY(), chunk.z() * 16 + 8);
                if (!ChunkRange.contains(chunk.x(), chunk.z(), cx, cz, radius) || !LoadedChunks.at(level,probe)) {
                    if (results.remove(chunk) != null) publishDetections();
                    continue;
                }
                activeScan = new ChunkScan(chunk, ScanHeight.blocksPerChunk(level));
            }
            ChunkScan scan = activeScan;
            // A one-chunk move can leave old work outside the radius without crossing
            // the queue-rebuild threshold. Check active work as well as cached results.
            if (!ChunkRange.contains(scan.chunk.x(), scan.chunk.z(), cx, cz, radius)
                    || !LoadedChunks.at(level,scan.chunk.x(),scan.chunk.z())) {
                if (results.remove(scan.chunk) != null) publishDetections();
                activeScan = null;
                continue;
            }
            int amount = Math.min(budget, scan.total - scan.cursor);
            for (int i = 0; i < amount; i++) {
                int index = scan.cursor + i;
                int x = scan.chunk.x() * 16 + (index % 256) / 16;
                int z = scan.chunk.z() * 16 + (index % 256) % 16;
                int y = level.getMinY() + index / 256;
                BlockState state = level.getBlockState(pos.set(x, y, z));
                scan.observe(state, x, y, z);
            }
            scan.cursor += amount;
            budget -= amount;
            if (scan.cursor >= scan.total) {
                if(scan.dirty){queue.remove(scan.chunk);queue.addFirst(scan.chunk);}
                else {baseIndex.put(scan.chunk.x,scan.chunk.z,scan.clues);results.put(scan.chunk, scan.detections());}
                activeScan = null;
                publishDetections();
            } else if (amount == 0) {
                activeScan = null;
            }
        }
        if (queue.isEmpty() && activeScan == null && nextRefreshTick <= tickCount)
            nextRefreshTick = tickCount + 40;
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        nearestDetections = Nearest.select(renderDetections, 64, d -> new Vec3(d.x(), d.y(), d.z()).distanceToSqr(camera));
    }

    private void rebuild(ClientLevel level, int cx, int cz, int radius) {
        queue.clear();
        activeScan = null;
        List<ChunkCoord> chunks = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++) chunks.add(new ChunkCoord(x, z));
        chunks.sort(Comparator.comparingLong(c -> square(c.x() - cx) + square(c.z() - cz)));
        for (ChunkCoord chunk : chunks) {
            BlockPos probe = new BlockPos(chunk.x() * 16 + 8, level.getMinY(), chunk.z() * 16 + 8);
            if (LoadedChunks.at(level,probe)) queue.addLast(chunk);
            else results.remove(chunk);
        }
        results.keySet().removeIf(c -> !ChunkRange.contains(c.x(), c.z(), cx, cz, radius));
        publishDetections();
        queueX = cx; queueZ = cz; queueDistance = radius;
    }

    private final class ChunkScan {
        private final ChunkCoord chunk;
        private final int total;
        private int cursor;
        private boolean dirty;
        private int budding;
        private long gx, gy, gz;
        private final BaseClues clues = new BaseClues();
        private int gxMin = Integer.MAX_VALUE, gyMin = Integer.MAX_VALUE, gzMin = Integer.MAX_VALUE;
        private int gxMax = Integer.MIN_VALUE, gyMax = Integer.MIN_VALUE, gzMax = Integer.MIN_VALUE;

        private ChunkScan(ChunkCoord chunk, int total) { this.chunk = chunk; this.total = total; }

        private void observe(BlockState state, int x, int y, int z) {
            if (settings.geodes() && state.is(Blocks.BUDDING_AMETHYST)) {
                budding++; gx += x; gy += y; gz += z;
                gxMin = Math.min(gxMin, x); gyMin = Math.min(gyMin, y); gzMin = Math.min(gzMin, z);
                gxMax = Math.max(gxMax, x); gyMax = Math.max(gyMax, y); gzMax = Math.max(gzMax, z);
            }
            observeBase(clues, state, x, y, z);
        }

        private List<Detection> detections() {
            List<Detection> found = new ArrayList<>(2);
            if (settings.geodes() && budding >= Math.max(1, 10 - settings.sensitivity()))
                found.add(new Detection(Kind.GEODE, (double) gx / budding, (double) gy / budding,
                        (double) gz / budding, budding,
                        new AABB(gxMin, gyMin, gzMin, gxMax + 1, gyMax + 1, gzMax + 1)));
            return found;
        }
    }

    private void publishDetections() {
        baseIndex.retain(results.keySet().stream().map(c->BaseClues.Index.key(c.x,c.z)).collect(java.util.stream.Collectors.toSet()));
        List<Detection> snapshot=new ArrayList<>();for(List<Detection> chunk:results.values())snapshot.addAll(chunk);
        for(var c:baseIndex.results(settings.sensitivity(),settings.strict()))snapshot.add(new Detection(Kind.ACTIVITY,c.x(),c.y(),c.z(),c.score(),
                new AABB(c.minX(),c.minY(),c.minZ(),c.maxX()+1,c.maxY()+1,c.maxZ()+1),c.key(),c.explanation()));
        allDetections=List.copyOf(snapshot);renderDetections=snapshot.stream().filter(d->!ignored(d)).toList();
        baseCount=(int)renderDetections.stream().filter(d->d.kind()==Kind.ACTIVITY).count();geodeCount=renderDetections.size()-baseCount;nearestDetections=List.of();
    }

    static void observeBase(BaseClues clues, BlockState s, int x, int y, int z) {
        if (s.is(Blocks.BEACON) || s.is(Blocks.RESPAWN_ANCHOR) || s.is(Blocks.CONDUIT)
                || s.is(Blocks.ENCHANTING_TABLE) || s.getBlock() instanceof ShulkerBoxBlock) {
            String label=s.is(Blocks.BEACON)?"beacon":s.is(Blocks.RESPAWN_ANCHOR)?"respawn anchor":s.is(Blocks.CONDUIT)?"conduit":s.is(Blocks.ENCHANTING_TABLE)?"enchanting table":"shulker box";
            clues.observe(x, y, z, BaseClues.Family.SPECIAL, 14, true, label);
        } else if (s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.ENDER_CHEST)) {
            clues.observe(x, y, z, BaseClues.Family.STORAGE, 4, false);
        } else if (s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER)
                || s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.ANVIL) || s.is(Blocks.CHIPPED_ANVIL)
                || s.is(Blocks.DAMAGED_ANVIL) || s.is(Blocks.BOOKSHELF) || s.is(Blocks.CHISELED_BOOKSHELF)) {
            clues.observe(x, y, z, BaseClues.Family.WORK, 3, false);
        } else if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH)
                || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.REDSTONE_TORCH) || s.is(Blocks.REDSTONE_WALL_TORCH)
                || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)) {
            clues.observe(x, y, z, BaseClues.Family.LIGHT, 2, false);
        } else if (s.is(Blocks.RAIL) || s.is(Blocks.POWERED_RAIL) || s.is(Blocks.DETECTOR_RAIL)
                || s.is(Blocks.ACTIVATOR_RAIL) || s.is(Blocks.REDSTONE_WIRE) || s.is(Blocks.REPEATER)
                || s.is(Blocks.COMPARATOR) || s.is(Blocks.LEVER)) {
            clues.observe(x, y, z, BaseClues.Family.TRANSPORT, 2, false);
        }
    }

    public void renderWorld(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || !FeatureAccess.allowed(client) || renderDetections.isEmpty()
                || context.consumers() == null || client.level != lastLevel) return;
        Vec3 camera = context.gameRenderer().getMainCamera().position();
        PoseStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(
                net.minecraft.client.renderer.rendertype.ChunkFinderThroughWallLines.renderType());
        for (Detection detection : nearestDetections) {
            String colorId = detection.kind() == Kind.GEODE ? "chunkfinder:geode" : "chunkfinder:base_clue";
            int color = colors.color(colorId, automaticColor(colorId));
            AABB bounds = detection.bounds();
            for (int layer = 2; layer >= 0; layer--) {
                double padding = 0.025 + layer * 0.045;
                float alpha = 1.0F - layer * 0.22F;
                int layeredColor = ((int) (alpha * 255) << 24) | (color & 0x00FFFFFF);
                AABB outline = bounds.inflate(padding);
                ShapeRenderer.renderShape(matrices, lines,
                        Shapes.box(outline.minX, outline.minY, outline.minZ, outline.maxX, outline.maxY, outline.maxZ),
                        -camera.x, -camera.y, -camera.z, layeredColor, 1.0F);
            }
        }
    }

    private static long square(int n) { return (long) n * n; }
    private record ChunkCoord(int x, int z) { }
    private record Detection(Kind kind,double x,double y,double z,int evidence,AABB bounds,String key,String explanation) {
        Detection(Kind kind,double x,double y,double z,int evidence,AABB bounds){this(kind,x,y,z,evidence,bounds,"geode:"+x+":"+y+":"+z,evidence+" budding blocks (natural)");}
    }
    private enum Kind { GEODE, ACTIVITY }
}
