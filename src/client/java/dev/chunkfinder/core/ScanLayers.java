package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Full-height coverage ordered by section distance from the viewing height. */
public final class ScanLayers {
    private ScanLayers() { }
    public static int[] ordered(int minY, int maxY, double viewY) {
        long height = (long) maxY - minY;
        if (height < 0 || height > 65536) throw new IllegalArgumentException("Unsupported scan height");
        if (height == 0) return new int[0];
        List<Integer> sections = new ArrayList<>();
        for (int section = minY >> 4; section <= (maxY - 1) >> 4; section++) sections.add(section);
        sections.sort(Comparator.<Integer>comparingDouble(section -> {
            long low = Math.max(minY, (long) section * 16), high = Math.min(maxY, (long) section * 16 + 16);
            return Math.abs((low + high) / 2.0 - viewY);
        }).thenComparingInt(Integer::intValue));
        int[] order = new int[(int) height]; int index = 0;
        for (int section : sections) {
            int low = (int) Math.max(minY, (long) section * 16);
            int high = (int) Math.min(maxY, (long) section * 16 + 16);
            for (int y = low; y < high; y++) order[index++] = y;
        }
        return order;
    }

    /** True when {@code layerIndex} begins 16 contiguous layers of one aligned section. */
    public static boolean fullSection(int[] layers, int layerIndex) {
        if (layers == null || layerIndex < 0 || layerIndex + 16 > layers.length) return false;
        int base = layers[layerIndex];
        if ((base & 15) != 0) return false;
        for (int i = 1; i < 16; i++) if (layers[layerIndex + i] != base + i) return false;
        return true;
    }
}
