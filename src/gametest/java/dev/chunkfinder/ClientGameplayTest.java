package dev.chunkfinder;

import com.google.gson.GsonBuilder;
import dev.chunkfinder.core.TerrainBoundary;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Explicit opt-in: disposable integrated-singleplayer world only, never a dedicated/public server. */
public final class ClientGameplayTest implements FabricClientGameTest {
    private int assertions;
    private void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        if (!"1".equals(System.getenv("CHUNKFINDER_WORLD_TEST"))) {
            System.out.println("Gameplay scenarios skipped (explicit CI opt-in required)."); return;
        }
        Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        if (!game.endsWith(Path.of("build/run/clientGameTest"))) throw new AssertionError("Gameplay test refused a non-isolated save directory: " + game);
        String variant = System.getenv().getOrDefault("CHUNKFINDER_RENDERER", "vanilla");
        var loader = FabricLoader.getInstance();
        check(loader.isModLoaded("sodium") == !variant.equals("vanilla"), "expected renderer must actually be loaded");
        check(loader.isModLoaded("iris") == variant.equals("iris"), "Iris variant must be separate from Sodium-only");
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("rendererVariant", variant); report.put("environment", "GitHub Actions / Xvfb / Mesa software rendering / 1280x720; not desktop GPU results");
        report.put("shaderPack", "none requested; actual state recorded with measurements");
        context.runOnClient(client -> { client.options.renderDistance().set(4); client.options.guiScale().set(2); client.options.framerateLimit().set(60); });
        context.getInput().resizeWindow(1280,720);
        ModuleManager modules = context.computeOnClient(client -> ChunkFinderClient.modules());
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamerule minecraft:immediate_respawn false");
            world.getServer().runCommand("gamerule minecraft:spawner_blocks_work false");
            world.getServer().runCommand("fill 0 60 0 15 88 15 minecraft:stone");
            world.getServer().runCommand("fill 4 63 4 11 70 11 minecraft:air");
            world.getServer().runCommand("setblock 8 68 10 minecraft:diamond_ore");
            world.getServer().runCommand("setblock 9 63 10 minecraft:chest");
            world.getServer().runCommand("setblock 10 63 10 minecraft:spawner{Delay:32767s}");
            world.getServer().runCommand("setblock 9 63 9 minecraft:beacon");
            world.getServer().runCommand("setblock 10 63 9 minecraft:crafting_table");
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("tp @a 8 64 8 0 0");
            world.getClientWorld().waitForChunksDownload(1200);
            context.setScreen(() -> null); context.waitTicks(40);
            check(context.computeOnClient(client -> client.level.getBlockState(new BlockPos(8,85,8)).is(Blocks.STONE)), "real downloaded underground test geometry exists");
            context.runOnClient(client -> { modules.freecam().setSpeed(2); modules.freecam().setAcceleration(0); modules.freecam().setDeceleration(0); modules.freecam().setSwapStrafe(false); modules.freecam().setFullTerrain(true); });
            Vec3 body = context.computeOnClient(client -> client.player.position());
            context.getInput().pressKey(GLFW.GLFW_KEY_G); context.waitTicks(2);
            check(context.computeOnClient(client -> modules.freecam().isEnabled() && client.getCameraEntity() != client.player && !client.smartCull), "actual G binding detaches camera and disables section occlusion");
            Vec3 camera = context.computeOnClient(client -> client.getCameraEntity().position());
            context.getInput().holdKeyFor(options -> options.keyLeft, 4); context.waitTick();
            check(context.computeOnClient(client -> client.getCameraEntity().getX() > camera.x + .3), "A moves screen-left in the real underground world");
            context.getInput().holdKeyFor(options -> options.keyRight, 4); context.waitTick();
            check(context.computeOnClient(client -> Math.abs(client.getCameraEntity().getX() - camera.x) < .25), "D reverses A displacement");
            check(context.computeOnClient(client -> client.player.position().distanceToSqr(body) < 1e-5), "Freecam movement does not move the actual player");
            context.runOnClient(client -> { modules.freecam().setHorizontalFlight(true); client.getCameraEntity().setXRot(-75); });
            double y = context.computeOnClient(client -> client.getCameraEntity().getY());
            context.getInput().holdKeyFor(options -> options.keyUp, 4); context.waitTick();
            check(context.computeOnClient(client -> Math.abs(client.getCameraEntity().getY() - y) < 1e-5), "horizontal mode remains level while looking up");
            context.getInput().holdKeyFor(options -> options.keyJump, 36); context.waitTicks(2);
            check(context.computeOnClient(client -> client.level.getBlockState(client.getCameraEntity().blockPosition()).is(Blocks.STONE)), "camera can enter loaded solid terrain without moving the body");
            context.takeScreenshot("gameplay-" + variant + "-underground");
            context.runOnClient(client -> client.getCameraEntity().setPos(512,80,512)); context.waitTicks(3);
            check(context.computeOnClient(client -> modules.freecam().boundary() == TerrainBoundary.Status.UNAVAILABLE), "real loaded-terrain boundary produces an explicit warning");
            check(context.computeOnClient(client -> !client.level.hasChunkAt(new BlockPos(512,64,512)) && client.player.position().distanceToSqr(body)<1e-5), "detached camera does not request or move the body to missing chunks");
            context.takeScreenshot("gameplay-" + variant + "-terrain-boundary");
            context.runOnClient(client -> client.getCameraEntity().setPos(8,66,8));
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("execute in minecraft:the_nether run tp @a 8 80 8");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(Level.NETHER) && !modules.freecam().isEnabled(),1200);
            check(context.computeOnClient(client -> client.getCameraEntity() == client.player), "dimension transfer releases the old-world camera");
            world.getServer().runCommand("execute in minecraft:overworld run tp @a 8 64 8");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(Level.OVERWORLD),1200);
            world.getClientWorld().waitForChunksDownload(1200); context.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            context.getInput().pressKey(GLFW.GLFW_KEY_G); context.waitTick();
            check(context.computeOnClient(client -> modules.freecam().isEnabled()), "freecam can re-enter after dimension transfer");
            world.getServer().runCommand("kill @a");
            context.waitForScreen(DeathScreen.class); context.waitTicks(25);
            check(context.computeOnClient(client -> !modules.freecam().isEnabled()), "real player death stops freecam");
            context.clickScreenButton("deathScreen.respawn");
            context.waitFor(client -> client.player != null && client.player.isAlive() && client.screen == null,1200);
            check(context.computeOnClient(client -> !modules.freecam().isEnabled() && client.getCameraEntity() == client.player), "respawn has a live player camera and no stale freecam owner");
            world.getServer().runCommand("gamemode creative @a"); world.getServer().runCommand("tp @a 8 64 8 0 0");
            context.waitTicks(100);
            context.runOnClient(client -> modules.diagnostics().reset()); context.waitTicks(200);
            report.put("baseline", context.computeOnClient(client -> modules.diagnostics().snapshot()));
            context.runOnClient(client -> {
                modules.itemEsp().setTargetIds(Set.of("minecraft:diamond_ore"));
                for (BlockEspModule module : java.util.List.of(modules.itemEsp(),modules.storageEsp(),modules.spawnerEsp(),modules.ancientDebrisFinder())) { module.setSearchRadius(2); module.setEnabled(true); }
                modules.entityEsp().setEnabled(true); modules.droppedItemEsp().setEnabled(true);
                modules.targetRenderer().setEnabled(true); modules.trajectories().setEnabled(true); modules.chunkFinder().setEnabled(true);
                modules.chunkFinder().adjustSimulationDistance(2 - modules.chunkFinder().simulationDistance());
                modules.diagnostics().reset();
            });
            int firstScan = context.waitFor(client -> modules.itemEsp().loadedChunkCount()>0 && modules.itemEsp().scannedChunkCount()>=modules.itemEsp().loadedChunkCount(),2400);
            report.put("firstBlockScanTicks", firstScan);
            check(context.computeOnClient(client -> modules.itemEsp().detectedCount() > 0), "production ESP finds the real test ore");
            context.runOnClient(client -> modules.diagnostics().reset()); context.waitTicks(200);
            var enabled = context.computeOnClient(client -> modules.diagnostics().snapshot()); report.put("modulesEnabled",enabled);
            check(enabled.cpuFrame().samples()>30, "benchmark records real rendered-frame samples");
            context.takeScreenshot("gameplay-" + variant + "-modules-enabled");
            context.runOnClient(client -> { for (Module module : modules.modules()) module.setEnabled(false); });
        }
        report.put("assertions", assertions); report.put("result","passed");
        try { Files.writeString(game.resolve("gameplay-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        System.out.println("Gameplay tests passed (" + assertions + " assertions) · " + variant);
        System.out.println("GAMEPLAY_MEASUREMENTS " + new GsonBuilder().create().toJson(report));
    }
}
