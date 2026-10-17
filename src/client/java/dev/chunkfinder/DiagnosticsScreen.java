package dev.chunkfinder;

import dev.chunkfinder.ui.UiTheme;
import dev.chunkfinder.ui.UiGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional read-only diagnostics; never adds persistent gameplay HUD clutter. */
final class DiagnosticsScreen extends Screen {
    private final Screen parent;
    private final ModuleManager modules;
    private List<String> lines = List.of();
    private int scroll, ticks;
    DiagnosticsScreen(Screen parent, ModuleManager modules) { super(Component.literal("Diagnostics")); this.parent = parent; this.modules = modules; }
    @Override protected void init() { refresh(); }
    @Override public void tick() { if (++ticks % 10 == 0) refresh(); }
    private void refresh() {
        var s = modules.diagnostics().snapshot(); List<String> next = new ArrayList<>();
        next.add("Chunk Finder " + s.version() + " · Minecraft 1.21.11");
        next.add("Renderer: " + s.renderer()); next.add("Shaders: " + s.shaders());
        next.add(String.format(Locale.ROOT, "CPU frame: %.2f ms median / %.2f ms p95 (%d samples)", s.cpuFrame().medianMs(), s.cpuFrame().p95Ms(), s.cpuFrame().samples()));
        next.add(String.format(Locale.ROOT, "Frame interval: %.2f ms p95 · heap %.1f / peak %.1f MiB", s.interval().p95Ms(), s.heapBytes()/1048576.0, s.peakHeapBytes()/1048576.0));
        next.add("Measurements include the current scene/UI; not a desktop FPS guarantee.");
        var reads = modules.blockReads().stats();
        next.add("Block reads this tick: " + reads.reads() + " · shared hits " + reads.hits() + " · cached " + reads.cached());
        next.add("Received updates " + modules.receivedBlockUpdates() + " · pending " + modules.pendingBlockUpdates() + " · overflow resets " + modules.blockUpdateOverflows());
        next.add("Freecam terrain: " + modules.freecam().boundary());
        next.add("Inside-block view: "+modules.freecam().viewStatus()+" · render overrides "+modules.freecam().cutawayMasks());
        next.add("Cutaway dirty sections: "+modules.freecam().cutawayDirtySections()+" · Sodium hook: "+SodiumCutawayBridge.status());
        for (BlockEspModule module : List.of(modules.itemEsp(), modules.storageEsp(), modules.spawnerEsp(), modules.ancientDebrisFinder())) {
            next.add(module.name() + ": " + (module.isEnabled() ? "on" : "off") + " · loaded in radius " + module.loadedChunkCount()
                    + " · scanned " + module.scannedChunkCount() + " · queued " + module.pendingChunkCount() + " · edits " + module.pendingUpdates() + " · skipped sections " + module.skippedSections());
            next.add("  " + module.targetSummary() + " · " + (module.exposedOnly() ? "exposed only" : "buried included") + " · " + module.detectedCount() + " candidates · " + module.policies().snapshot().targets().size() + " policy overrides");
        }
        next.add("Base clues: " + modules.chunkFinder().baseCount() + " · strict " + modules.chunkFinder().strictBaseClues());
        next.add("Loaded/client-reported data only. Server obfuscation cannot be authenticated.");
        lines = List.copyOf(next);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible())));
    }
    private int visible() { return Math.max(1, (height - 92) / 15); }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) { super.renderBackground(g,x,y,delta); g.fill(0,0,width,height,0x70000000); }
    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        var canvas = new MinecraftUiCanvas(g, font); Rect panel = new Rect(12,12,width-24,height-24);
        UiTheme.outlined(canvas,panel,8,UiTheme.BORDER,UiTheme.PANEL);
        UiTheme.text(canvas,"DIAGNOSTICS",24,24,width-48,UiTheme.TEXT);
        for (int i=0; i<visible() && scroll+i<lines.size(); i++) UiTheme.text(canvas,lines.get(scroll+i),24,46+i*15,width-48,UiTheme.TEXT);
        UiTheme.text(canvas,"Scroll / Up / Down · R refresh · Esc back",24,height-32,width-48,UiTheme.MUTED);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v) { if (!Double.isFinite(v) || v==0) return false; scroll = Math.max(0,Math.min(Math.max(0,lines.size()-visible()),scroll+(v>0?-1:1)));return true; }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key()==264 || event.key()==265) { scroll=Math.max(0,Math.min(Math.max(0,lines.size()-visible()),scroll+(event.key()==264?1:-1)));triggerImmediateNarration(false);return true; }
        if (event.key()==82) { refresh();return true; } return super.keyPressed(event);
    }
    @Override public Component getNarrationMessage() { return Component.literal("Diagnostics. " + (lines.isEmpty()?"":lines.get(Math.min(scroll,lines.size()-1)))); }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
