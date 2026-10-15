package dev.chunkfinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chunkfinder.core.Nearest;
import dev.chunkfinder.core.TrajectoryPredictor;
import dev.chunkfinder.core.TrajectoryPredictor.Point;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/** Bounded, approximate client-side paths. Collision work runs once per tick, never per render frame. */
public final class TrajectoriesModule extends Module {
    private static final int MAX_TRACKED = 24;
    private final OverlaySettings settings = new OverlaySettings("chunkfinder-trajectories.json", 36, 12, 72);
    private record Path(Entity source, List<Point> points, int color) { }
    private List<Path> paths = List.of();
    private ClientLevel lastLevel;
    private int visibleCount;

    public TrajectoriesModule() {
        super("Trajectories", ModuleCategory.VISUALS, false);
        super.setEnabled(settings.enabled());
    }

    @Override public void setEnabled(boolean value) {
        if (value == isEnabled()) return;
        super.setEnabled(value); settings.setEnabled(value); invalidate();
    }
    public int visibleCount() { return visibleCount; }
    public int steps() { return settings.range(); }
    public void setSteps(int value) {
        int before = settings.range(); settings.setRange(value);
        if (before != settings.range()) invalidate();
    }
    public boolean showItems() { return settings.option(); }
    public void setShowItems(boolean value) {
        boolean before = settings.option(); settings.setOption(value);
        if (before != settings.option()) invalidate();
    }
    private void invalidate() { paths = List.of(); lastLevel = null; visibleCount = 0; }

    public void tick(Minecraft client) {
        if (!isEnabled() || !FeatureAccess.allowed(client)) { invalidate(); return; }
        ClientLevel level = client.level;
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        Nearest<Entity> nearest = new Nearest<>(MAX_TRACKED);
        for (Entity entity : level.entitiesForRendering()) {
            boolean item = entity instanceof ItemEntity;
            if (!(entity instanceof Projectile) && !(item && showItems())) continue;
            double distance = entity.position().distanceToSqr(camera);
            if (entity.isRemoved() || !entity.isAlive() || distance > 96.0 * 96.0
                    || entity.getDeltaMovement().lengthSqr() < 0.0004) continue;
            nearest.offer(entity, distance);
        }
        List<Path> next = new ArrayList<>();
        for (Entity entity : nearest.values()) {
            List<Point> points = TrajectoryPredictor.predict(TrajectoryQueries.point(entity.position()),
                    TrajectoryQueries.point(entity.getDeltaMovement()), entity.isNoGravity() ? 0 : entity.getGravity(),
                    steps(), TrajectoryQueries.view(level, pos -> LoadedChunks.at(level,pos), CollisionContext.of(entity)));
            if (!points.isEmpty()) next.add(new Path(entity, points,
                    entity instanceof ItemEntity ? 0xFFFFD69A : 0xFF80D5FF));
        }
        paths = List.copyOf(next);
        lastLevel = level;
        visibleCount = 0;
    }

    public void renderWorld(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        visibleCount = 0;
        if (!isEnabled() || !FeatureAccess.allowed(client) || client.level != lastLevel
                || context.consumers() == null || paths.isEmpty()) return;
        Vec3 camera = context.gameRenderer().getMainCamera().position();
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer lines = context.consumers().getBuffer(
                net.minecraft.client.renderer.rendertype.ChunkFinderThroughWallLines.renderType());
        for (Path path : paths) {
            if (path.source.isRemoved() || !path.source.isAlive() || path.source.level() != client.level) continue;
            for (int i = 1; i < path.points.size(); i++) {
                addSegment(lines, pose, path.points.get(i - 1), path.points.get(i), camera, path.color);
            }
            visibleCount++;
        }
    }

    private static void addSegment(VertexConsumer lines, PoseStack.Pose pose, Point a, Point b, Vec3 camera, int color) {
        lines.addVertex(pose, (float) (a.x() - camera.x), (float) (a.y() - camera.y), (float) (a.z() - camera.z))
                .setColor(color).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(1.8F);
        lines.addVertex(pose, (float) (b.x() - camera.x), (float) (b.y() - camera.y), (float) (b.z() - camera.z))
                .setColor(color).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(1.8F);
    }

}
