package dev.chunkfinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chunkfinder.core.FairTargetBuffer;
import dev.chunkfinder.core.TargetPolicies;
import dev.chunkfinder.settings.TargetPolicyConfig;
import dev.chunkfinder.settings.PaletteConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.MobCategory;
import dev.chunkfinder.core.TargetSelection.Entry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
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
    private final PaletteConfig colors;
    private final TargetPolicyConfig policies;
    private Set<EntityType<?>> entityTypes = Set.of();
    private Set<Item> items = Set.of();
    private List<Entity> nearest = List.of();
    private int nearbyCount;
    private ClientLevel lastLevel;

    public EntityEspModule(Mode mode) {
        super(mode == Mode.ENTITIES ? "Entity ESP" : "Dropped Item ESP", ModuleCategory.VISUALS, false);
        this.mode = mode;
        policies=new TargetPolicyConfig(FabricLoader.getInstance().getConfigDir().resolve(mode==Mode.ENTITIES?"chunkfinder-rules-entities.json":"chunkfinder-rules-items.json"));
        colors = new PaletteConfig(FabricLoader.getInstance().getConfigDir().resolve(mode == Mode.ENTITIES ? "chunkfinder-colors-entities.json" : "chunkfinder-colors-items.json"), mode == Mode.ENTITIES ? 0xFF89E6B6 : 0xFFFFD27B);
        settings = new OverlaySettings(mode == Mode.ENTITIES ? "chunkfinder-entities.json" : "chunkfinder-dropped-items.json",
                48, 16, 128, mode == Mode.ENTITIES ? "entityIds" : "itemIds",
                mode == Mode.ENTITIES ? TargetCatalog.entityDefaults() : TargetCatalog.itemDefaults());
        resolveTargets();
        super.setEnabled(settings.enabled());
    }

    public TargetPolicyConfig policies(){return policies;}
    public void setPolicies(TargetPolicies.Snapshot value){if(policies.snapshot().equals(value))return;policies.apply(value);invalidate();}
    public PaletteConfig colors() { return colors; }
    public int detectedCount() { return nearbyCount; }
    public int automaticColor(String id) {
        if (mode == Mode.DROPPED_ITEMS) return 0xFFFFD27B;
        if ("minecraft:player".equals(id)) return 0xFF82B6FF;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
            if (BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(id))
                return type.getCategory() == MobCategory.MONSTER ? 0xFFFF808D : 0xFF89E6B6;
        return 0xFF89E6B6;
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
        FairTargetBuffer<Entity> found = new FairTargetBuffer<>(MAX_OUTLINES,policies.snapshot(),true);
        nearbyCount = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !matches(entity)) continue;
            double distance = entity.position().distanceToSqr(viewpoint);
            if (distance > rangeSquared) continue;
            String id=targetId(entity);var rule=policies.snapshot().rule(id);
            if(rule.limit()==0 || rule.range()>0 && distance>(double)rule.range()*rule.range())continue;
            nearbyCount++;
            found.offer(entity,id,distance);
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
            var rule=policies.snapshot().rule(targetId(entity));
            double maximum=rule.range()==0?range():Math.min(range(),rule.range());
            if(rule.limit()==0 || entity.position().distanceToSqr(camera)>maximum*maximum)continue;
            AABB box = entity.getBoundingBox().inflate(0.07);
            ShapeRenderer.renderShape(matrices, lines, Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
                    -camera.x, -camera.y, -camera.z, color(entity), 1.0F);
        }
    }
    private String targetId(Entity entity) {
        return mode==Mode.DROPPED_ITEMS && entity instanceof ItemEntity item
                ? BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString()
                : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
    private int color(Entity entity) {
        if (mode == Mode.DROPPED_ITEMS && entity instanceof ItemEntity item)
            return colors.color(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(), 0xFFFFD27B);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        int fallback = entity instanceof Player ? 0xFF82B6FF
                : entity.getType().getCategory() == MobCategory.MONSTER ? 0xFFFF808D : 0xFF89E6B6;
        return colors.color(id, fallback);
    }

}
