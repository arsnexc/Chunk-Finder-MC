package dev.chunkfinder.core;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Named, label-backed presets; not a promise of suitability for every form of color vision. */
public final class PalettePresets {
    private PalettePresets(){}
    public static EspPalette.Snapshot apply(String preset,EspPalette.Snapshot before,Collection<String> selected){
        int[] colors=switch(preset){
            case "okabe-ito" -> new int[]{0xFFE69F00,0xFF56B4E9,0xFF009E73,0xFFF0E442,0xFF0072B2,0xFFD55E00,0xFFCC79A7,0xFFFFFFFF};
            case "contrast" -> new int[]{0xFFFFFFFF,0xFF00FFFF,0xFFFF00FF,0xFFFFC000,0xFF80FF40,0xFF80A8FF};
            case "soft" -> new int[]{0xFF8CE0BD,0xFF82B6FF,0xFFFFD27B,0xFFE8A5CC,0xFFC2B0FF,0xFF89DCE6};
            default -> throw new IllegalArgumentException("Unknown palette preset");
        };
        Map<String,Integer> targets=new TreeMap<>(before.targets());int index=0;
        for(String id:TargetIds.copy(selected))targets.put(id,colors[index++%colors.length]);
        return new EspPalette.Snapshot(colors[0],targets);
    }
}
