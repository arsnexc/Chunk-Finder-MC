package dev.chunkfinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChunkFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "chunkfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "modules"));
    private static final KeyMapping OPEN_MODULES = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.chunkfinder.open_modules", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY));
    private static final KeyMapping TOGGLE_FREECAM = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.chunkfinder.toggle_freecam", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY));
    private static final KeyMapping FREECAM_SPEED = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.chunkfinder.freecam_speed", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY));
    private static ChunkFinderClient instance;
    private final ModuleManager modules = new ModuleManager();
    public static ModuleManager modules() {
        if (instance == null) throw new IllegalStateException("Client has not initialized");
        return instance.modules;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        ServerAuthorization.register();
        ClientTickEvents.START_CLIENT_TICK.register(modules.freecam()::beginTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MODULES.consumeClick()) {
                if (client.screen == null) client.setScreen(new ModuleSettingsScreen(modules));
            }
            while (TOGGLE_FREECAM.consumeClick()) {
                if (!modules.freecam().toggle(client)) {
                    LOGGER.info("Freecam needs a loaded world and player.");
                }
            }
            while (FREECAM_SPEED.consumeClick()) if (modules.freecam().isEnabled()) modules.freecam().nextSpeedPreset();
            if (modules.chunkFinder().isEnabled()) modules.chunkFinder().tick(client);
            if (modules.ancientDebrisFinder().isEnabled()) modules.ancientDebrisFinder().tick(client);
            if (modules.itemEsp().isEnabled()) modules.itemEsp().tick(client);
            if (modules.storageEsp().isEnabled()) modules.storageEsp().tick(client);
            if (modules.spawnerEsp().isEnabled()) modules.spawnerEsp().tick(client);
            modules.entityEsp().tick(client);
            modules.droppedItemEsp().tick(client);
            modules.trajectories().tick(client);
            modules.freecam().tick(client);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "compact_hud"),
                (graphics, deltaTracker) -> modules.renderHud(Minecraft.getInstance(), graphics));
        WorldRenderEvents.END_MAIN.register(context -> modules.diagnostics().record(Minecraft.getInstance()));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.chunkFinder()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.ancientDebrisFinder()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.itemEsp()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.storageEsp()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.spawnerEsp()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.entityEsp()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.droppedItemEsp()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.trajectories()::renderWorld);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(modules.targetRenderer()::renderWorld);
    }
}
