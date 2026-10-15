package dev.chunkfinder.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Visibility/pins affect presentation, never module state. Critical terrain warnings stay first. */
public final class HudRows {
    public record Item(String id,CompactHud.Row row,boolean zero,boolean critical){}
    private HudRows(){}
    public static List<CompactHud.Row> select(List<Item> items,Set<String> hidden,Set<String> pinned,boolean hideZeros){
        return items.stream().filter(item->item.critical || !hidden.contains(item.id) && (!hideZeros || !item.zero || pinned.contains(item.id)))
                .sorted(Comparator.comparingInt(item->item.critical?0:pinned.contains(item.id)?1:2)).map(Item::row).toList();
    }
}
