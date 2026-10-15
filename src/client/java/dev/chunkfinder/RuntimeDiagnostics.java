package dev.chunkfinder;

import dev.chunkfinder.core.FrameSamples;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Client-thread telemetry, also used by the isolated gameplay benchmark. */
public final class RuntimeDiagnostics {
    public record Snapshot(String version, String renderer, String shaders, FrameSamples.Summary cpuFrame,
                           FrameSamples.Summary interval, long heapBytes, long peakHeapBytes, long frames) { }
    private final FrameSamples cpu = new FrameSamples(1200), interval = new FrameSamples(1200);
    private long last, peakHeap, frames;
    public void record(Minecraft client) {
        long now = System.nanoTime();
        if (last != 0) interval.add(now - last);
        last = now; cpu.add(client.getFrameTimeNs()); frames++;
        peakHeap = Math.max(peakHeap, usedHeap());
    }
    public void reset() { cpu.clear(); interval.clear(); last = peakHeap = frames = 0; }
    private static long usedHeap() { Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }
    public Snapshot snapshot() {
        long heap=usedHeap();peakHeap=Math.max(peakHeap,heap);
        return new Snapshot(version("chunkfinder"), renderer(), shaderState(), cpu.summary(), interval.summary(), heap, peakHeap, frames);
    }
    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("absent");
    }
    public static String renderer() {
        var loader = FabricLoader.getInstance();
        return loader.isModLoaded("iris") ? "Iris " + version("iris") + " / Sodium " + version("sodium")
                : loader.isModLoaded("sodium") ? "Sodium " + version("sodium") : "Vanilla Fabric";
    }
    private static String shaderState() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return "not installed";
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(api.getMethod("isShaderPackInUse").invoke(instance)) ? "active" : "disabled";
        } catch (ReflectiveOperationException | LinkageError e) { return "unknown (API unavailable)"; }
    }
}
