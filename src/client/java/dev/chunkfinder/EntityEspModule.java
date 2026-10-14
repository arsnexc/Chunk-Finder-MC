package dev.chunkfinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chunkfinder.core.Nearest;
import dev.chunkfinder.core.TargetSelection.Entry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Multi-target entity types / dropped item IDs. Only client-visible entities are inspected. */
public final class EntityEspModule extends Module {
    public enum Mode { ENTITIES, DROPPED_ITEMS }
    private static final int MAX_OUTLINES = 32;
    private final Mode mode;
    private final OverlaySettings settings;
    private Set<EntityType<?>> entityTypes = Set.of();
    private Set<Item> items = Set.of();
    private List<Entity> nearest = List.of();
    private int nearbyCount;
    private ClientLevel lastLevel;

    public EntityEspModule(Mode mode) {
        super(mode == Mode.ENTITIES ? "Entity ESP" : "Dropped Item ESP", ModuleCategory.VISUALS, false);
        this.mode = mode;
        settings = new OverlaySettings(mode == Mode.ENTITIES ? "chunkfinder-entities.json" : "chunkfinder-dropped-items.json",
                48, 16, 128, mode == Mode.ENTITIES ? "entityIds" : "itemIds",
                mode == Mode.ENTITIES ? TargetCatalog.entityDefaults() : TargetCatalog.itemDefaults());
        resolveTargets();
        super.setEnabled(settings.enabled());
    }

    public Mode mode() { return mode; }
    public int range() { return settings.range(); }
    public void setRange(int value) { settings.setRange(value); invalidate(); }
    public boolean option() { return settings.option(); }
    public void setOption(boolean value) { settings.setOption(value); invalidate(); }
    public Set<String> targetIds() { return settings.targets(); }
    public List<Entry> targetChoices() { return mode == Mode.ENTITIES ? TargetCatalog.entities() : TargetCatalog.items(); }
    public String targetSummary() {
        if (targetIds().isEmpty()) return "None selected";
        int missing = targetIds().size() - (mode == Mode.ENTITIES ? entityTypes.size() : items.size());
        return targetIds().size() + " selected" + (missing > 0 ? " · " + missing + " unavailable" : "");
    }
    public void setTargetIds(Collection<String> ids) { settings.setTargets(ids); resolveTargets(); invalidate(); }
    @Override public void setEnabled(boolean value) { super.setEnabled(value); settings.setEnabled(value); invalidate(); }

    private void resolveTargets() {
        if (mode == Mode.ENTITIES) {
            Set<EntityType<?>> resolved = new HashSet<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
                if (settings.targets().contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())) resolved.add(type);
            entityTypes = Set.copyOf(resolved);
        } else {
            Set<Item> resolved = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) if (item != Items.AIR
                    && settings.targets().contains(BuiltInRegistries.ITEM.getKey(item).toString())) resolved.add(item);
            items = Set.copyOf(resolved);
        }
    }
    private void invalidate() { nearest = List.of(); nearbyCount = 0; lastLevel = null; }
    private boolean matches(Entity entity) {
        if (entity.isRemoved() || !entity.isAlive()) return false;
        if (mode == Mode.DROPPED_ITEMS)
            return entity instanceof ItemEntity item && !item.getItem().isEmpty() && items.contains(item.getItem().getItem());
        return entityTypes.contains(entity.getType()) && (option() || !(entity instanceof Player));
    }

    public void tick(Minecraft client) {
        if (!isEnabled() || !FeatureAccess.allowed(client)) { invalidate(); return; }
        lastLevel = client.level;
        if (settings.targets().isEmpty()) { nearest = List.of(); nearbyCount = 0; return; }
        Vec3 viewpoint = client.gameRenderer.getMainCamera().position();
        double rangeSquared = (double) range() * range();
        Nearest<Entity> found = new Nearest<>(MAX_OUTLINES);
        nearbyCount = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !matches(entity)) continue;
            double distance = entity.position().distanceToSqr(viewpoint);
            if (distance > rangeSquared) continue;
            nearbyCount++;
            found.offer(entity, distance);
        }
        nearest = found.values();
    }

    public void renderWorld(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || !FeatureAccess.allowed(client) || client.level != lastLevel || context.consumers() == null) return;
        Vec3 camera = context.gameRenderer().getMainCamera().position();
        PoseStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(
                net.minecraft.client.renderer.rendertype.ChunkFinderThroughWallLines.renderType());
        for (Entity entity : nearest) {
            if (!matches(entity)) continue;
            AABB box = entity.getBoundingBox().inflate(0.07);
            ShapeRenderer.renderShape(matrices, lines, Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
                    -camera.x, -camera.y, -camera.z, color(entity), 1.0F);
        }
    }
    private int color(Entity entity) {
        if (mode == Mode.DROPPED_ITEMS) return 0xFFFFD27B;
        if (entity instanceof Player) return 0xFF82B6FF;
        return entity instanceof Monster ? 0xFFFF808D : 0xFF89E6B6;
    }

    public void renderHud(Minecraft client, GuiGraphics g) {
        if (!isEnabled() || !FeatureAccess.allowed(client) || client.level != lastLevel || client.options.hideGui || client.screen != null) return;
        int x = 12;
        int y = mode == Mode.ENTITIES ? 157 : Math.max(12, g.guiHeight() - 123);
        int count = Math.min(2, nearest.size()), height = 31 + count * 14;
        if (y + height > g.guiHeight() - 4) y = Math.max(4, g.guiHeight() - height - 4);
        g.fill(x, y, x + 214, y + height, 0xDA171717);
        g.fill(x, y, x + 2, y + height, mode == Mode.ENTITIES ? 0xFF82B6FF : 0xFFFFD27B);
        String title = mode == Mode.ENTITIES ? "ENTITY ESP" : "DROPPED ITEM ESP";
        g.drawString(client.font, Component.literal(title), x + 9, y + 7, 0xFFE5E5E5, true);
        String total = Integer.toString(nearbyCount);
        g.drawString(client.font, Component.literal(total), x + 204 - client.font.width(total), y + 7, 0xFFAAAAAA, false);
        if (settings.targets().isEmpty()) g.drawString(client.font, Component.literal("No targets selected"), x + 9, y + 20, 0xFFAAAAAA, false);
        for (int i = 0; i < count; i++) {
            Entity entity = nearest.get(i);
            String label;
            if (entity instanceof ItemEntity item) {
                label = item.getItem().getHoverName().getString();
                if (option()) label += " ×" + item.getItem().getCount();
            } else label = entity.getName().getString();
            if (client.font.width(label) > 188) label = client.font.substrByWidth(Component.literal(label), 182).getString() + "…";
            g.drawString(client.font, Component.literal(label), x + 9, y + 23 + i * 14, 0xFFD0D0D0, false);
        }
    }
}
