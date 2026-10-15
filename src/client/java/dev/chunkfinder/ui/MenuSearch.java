package dev.chunkfinder.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Searches labels, IDs, categories and help text. Results reveal controls, never activate them. */
public final class MenuSearch {
    public record Hit(String id,String category,String moduleId,String settingId,String label,String location,String hint){}
    private final List<Hit> entries;
    public MenuSearch(List<ClickGuiModel.Category> categories){
        List<Hit> result=new ArrayList<>();
        for(var category:categories)for(var entry:category.entries()){
            result.add(new Hit(entry.id(),category.id(),entry.id(),null,entry.label(),category.label(),entry.hint()));
            for(var setting:entry.settings())result.add(new Hit(entry.id()+"/"+setting.id(),category.id(),entry.id(),setting.id(),setting.label(),category.label()+" / "+entry.label(),setting.hint()));
        }
        entries=List.copyOf(result);
    }
    public List<Hit> find(String query,Set<String> favorites,boolean onlyFavorites){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);String[] terms=q.isEmpty()?new String[0]:q.split("\\s+");
        return entries.stream().filter(hit->{
            if(onlyFavorites && !favorites.contains(hit.id))return false;
            String haystack=(hit.id+" "+hit.label+" "+hit.location+" "+hit.hint).toLowerCase(Locale.ROOT);
            for(String term:terms)if(!haystack.contains(term))return false;return true;
        }).sorted(Comparator.comparingInt((Hit h)->favorites.contains(h.id)?0:1)
                .thenComparingInt(h->h.label.toLowerCase(Locale.ROOT).startsWith(q)?0:1)
                .thenComparing(Hit::label,String.CASE_INSENSITIVE_ORDER).thenComparing(Hit::id)).toList();
    }
}
