package dev.chunkfinder.settings;

import com.google.gson.JsonArray;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Persistent presentation/accessibility preferences; never changes module enable states. */
public final class InterfaceConfig {
    private final JsonConfig config;
    private boolean hud, backgrounds, details, reducedMotion, contrast, hints;
    private int lines, hudScale, menuScale;
    private Set<String> minimized;
    private java.util.List<String> panelOrder=java.util.List.of();
    private Set<String> favorites=Set.of(),hiddenHud=Set.of(),pinnedHud=Set.of();
    private boolean hideZeroRows,snapHud;
    private int hudX,hudY,hudOpacity;
    public InterfaceConfig(Path path) {
        config = new JsonConfig(path);
        hud = config.bool("hud", true); backgrounds = config.bool("hudBackgrounds", false);
        details = config.bool("hudDetails", false); reducedMotion = config.bool("reducedMotion", false);
        contrast = config.bool("highContrast", false); hints = config.bool("tooltips", true);
        lines = config.integer("hudLines", 6, 2, 10); hudScale = config.integer("hudScale", 90, 60, 140);
        menuScale = config.integer("menuScale", 100, 75, 150);
        TreeSet<String> ids = new TreeSet<>();
        var value = config.element("minimizedPanels");
        if (value.isJsonArray()) for (var entry : value.getAsJsonArray())
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() && valid(entry.getAsString())) ids.add(entry.getAsString());
        minimized = Set.copyOf(ids);
        var order=config.element("panelOrder");java.util.LinkedHashSet<String> ordered=new java.util.LinkedHashSet<>();
        if(order.isJsonArray())for(var item:order.getAsJsonArray())if(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() && valid(item.getAsString()))ordered.add(item.getAsString());
        panelOrder=java.util.List.copyOf(ordered);
        var storedFavorites=config.element("favorites");TreeSet<String> favoriteIds=new TreeSet<>();
        if(storedFavorites.isJsonArray())for(var item:storedFavorites.getAsJsonArray())if(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() && validFavorite(item.getAsString()))favoriteIds.add(item.getAsString());
        favorites=Set.copyOf(favoriteIds);
        hiddenHud=readIds("hiddenHudRows");pinnedHud=readIds("pinnedHudRows");hideZeroRows=config.bool("hideZeroRows",false);snapHud=config.bool("snapHud",true);
        hudX=config.integer("hudX",0,0,1000);hudY=config.integer("hudY",0,0,1000);hudOpacity=config.integer("hudOpacity",70,0,90);
    }
    private Set<String> readIds(String key){TreeSet<String> result=new TreeSet<>();var data=config.element(key);if(data.isJsonArray())for(var e:data.getAsJsonArray())if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()&&valid(e.getAsString()))result.add(e.getAsString());return Set.copyOf(result);}
    public Set<String> hiddenHud(){return hiddenHud;}public Set<String> pinnedHud(){return pinnedHud;}
    public boolean hideZeroRows(){return hideZeroRows;}public boolean snapHud(){return snapHud;}
    public int hudX(){return hudX;}public int hudY(){return hudY;}public int hudOpacity(){return hudOpacity;}
    public void hudPosition(int x,int y){int nx=Math.max(0,Math.min(1000,x)),ny=Math.max(0,Math.min(1000,y));if(nx!=hudX||ny!=hudY){hudX=nx;hudY=ny;save();}}
    public void hudOpacity(int value){int n=Math.max(0,Math.min(90,value));if(n!=hudOpacity){hudOpacity=n;save();}}
    public void hideZeroRows(boolean value){if(value!=hideZeroRows){hideZeroRows=value;save();}}
    public void snapHud(boolean value){if(value!=snapHud){snapHud=value;save();}}
    public void rowVisible(String id,boolean visible){TreeSet<String> copy=new TreeSet<>(hiddenHud);if(visible)copy.remove(id);else if(valid(id))copy.add(id);if(!copy.equals(hiddenHud)){hiddenHud=Set.copyOf(copy);save();}}
    public void rowPinned(String id,boolean pinned){TreeSet<String> copy=new TreeSet<>(pinnedHud);if(pinned&&valid(id))copy.add(id);else copy.remove(id);if(!copy.equals(pinnedHud)){pinnedHud=Set.copyOf(copy);save();}}
    private static boolean validFavorite(String id){return id!=null && id.length()<=100 && id.matches("[a-z0-9_-]+(/[a-z0-9_-]+)?");}
    public Set<String> favorites(){return favorites;}
    public void toggleFavorite(String id){if(!validFavorite(id))return;TreeSet<String> ids=new TreeSet<>(favorites);if(!ids.add(id))ids.remove(id);favorites=Set.copyOf(ids);save();}
    private static boolean valid(String id) { return id != null && id.matches("[a-z0-9_-]{1,48}"); }
    public boolean hud() { return hud; }
    public boolean backgrounds() { return backgrounds; }
    public boolean details() { return details; }
    public boolean reducedMotion() { return reducedMotion; }
    public boolean contrast() { return contrast; }
    public boolean hints() { return hints; }
    public int lines() { return lines; }
    public int hudScale() { return hudScale; }
    public int menuScale() { return menuScale; }
    public java.util.List<String> panelOrder(){return panelOrder;}
    public void panelOrder(java.util.Collection<String> order){java.util.LinkedHashSet<String> clean=new java.util.LinkedHashSet<>();for(String id:order)if(valid(id))clean.add(id);var value=java.util.List.copyOf(clean);if(!value.equals(panelOrder)){panelOrder=value;save();}}
    public Set<String> minimized() { return minimized; }
    public void hud(boolean v) { if (v != hud) { hud = v; save(); } }
    public void backgrounds(boolean v) { if (v != backgrounds) { backgrounds = v; save(); } }
    public void details(boolean v) { if (v != details) { details = v; save(); } }
    public void reducedMotion(boolean v) { if (v != reducedMotion) { reducedMotion = v; save(); } }
    public void contrast(boolean v) { if (v != contrast) { contrast = v; save(); } }
    public void hints(boolean v) { if (v != hints) { hints = v; save(); } }
    public void lines(int v) { int n = Math.max(2, Math.min(10, v)); if (lines != n) { lines = n; save(); } }
    public void hudScale(int v) { int n = Math.max(60, Math.min(140, v)); if (hudScale != n) { hudScale = n; save(); } }
    public void menuScale(int v) { int n = Math.max(75, Math.min(150, v)); if (menuScale != n) { menuScale = n; save(); } }
    public void minimized(Collection<String> ids) {
        TreeSet<String> clean = new TreeSet<>(); for (String id : ids) if (valid(id)) clean.add(id);
        if (!minimized.equals(clean)) { minimized = Set.copyOf(clean); save(); }
    }
    private void save() {
        config.put("hud", hud); config.put("hudBackgrounds", backgrounds); config.put("hudDetails", details);
        config.put("reducedMotion", reducedMotion); config.put("highContrast", contrast); config.put("tooltips", hints);
        config.put("hudLines", lines); config.put("hudScale", hudScale); config.put("menuScale", menuScale);
        JsonArray panels = new JsonArray(); new TreeSet<>(minimized).forEach(panels::add);
        config.putElement("minimizedPanels", panels);
        JsonArray order=new JsonArray();panelOrder.forEach(order::add);config.putElement("panelOrder",order);JsonArray stars=new JsonArray();new TreeSet<>(favorites).forEach(stars::add);config.putElement("favorites",stars);
        JsonArray hidden=new JsonArray(),pinned=new JsonArray();new TreeSet<>(hiddenHud).forEach(hidden::add);new TreeSet<>(pinnedHud).forEach(pinned::add);
        config.putElement("hiddenHudRows",hidden);config.putElement("pinnedHudRows",pinned);config.put("hideZeroRows",hideZeroRows);config.put("snapHud",snapHud);
        config.put("hudX",hudX);config.put("hudY",hudY);config.put("hudOpacity",hudOpacity);config.save();
    }
}
