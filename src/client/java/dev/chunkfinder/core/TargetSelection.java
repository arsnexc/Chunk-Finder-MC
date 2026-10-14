package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Transactional picker model. Only apply() invokes the module; closing/canceling never does. */
public final class TargetSelection {
    public record Entry(String id, String name, boolean available) {
        public Entry(String id, String name) { this(id, name, true); }
    }
    private final List<Entry> entries;
    private final Set<String> original;
    private final TreeSet<String> selected;
    private String query = "";
    private boolean selectedOnly;
    private boolean finished;
    private List<Entry> cached;

    public TargetSelection(Collection<Entry> entries, Collection<String> initial) {
        original = TargetIds.copy(initial);
        selected = new TreeSet<>(original);
        LinkedHashMap<String, Entry> unique = new LinkedHashMap<>();
        for (Entry entry : entries) unique.putIfAbsent(entry.id(), entry);
        // Do not silently delete IDs belonging to a temporarily missing mod on Apply.
        for (String id : original) unique.putIfAbsent(id, new Entry(id, "Unavailable target", false));
        List<Entry> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(Entry::id));
        this.entries = List.copyOf(sorted);
    }

    public void query(String query) { this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT); cached = null; }
    public void selectedOnly(boolean selectedOnly) { this.selectedOnly = selectedOnly; cached = null; }
    public boolean selectedOnly() { return selectedOnly; }
    public int count() { return selected.size(); }
    public int unavailableCount() { return (int) entries.stream().filter(e -> !e.available() && selected.contains(e.id())).count(); }
    public boolean selected(String id) { return selected.contains(id); }
    public boolean changed() { return !original.equals(selected); }
    public Set<String> snapshot() { return TargetIds.copy(selected); }

    public List<Entry> filtered() {
        if (cached != null) return cached;
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        cached = entries.stream().filter(entry -> {
            if (selectedOnly && !selected.contains(entry.id())) return false;
            String haystack = (entry.id() + " " + entry.name()).toLowerCase(Locale.ROOT);
            for (String term : terms) if (!haystack.contains(term)) return false;
            return true;
        }).toList();
        return cached;
    }

    public void toggle(String id) {
        if (finished || entries.stream().noneMatch(e -> e.id().equals(id))) return;
        if (!selected.add(id)) selected.remove(id);
        if (selectedOnly) cached = null;
    }

    /** Bulk actions affect the entire filtered result, not just the currently visible page. */
    public void selectFiltered() {
        if (finished) return;
        for (Entry entry : filtered()) if (entry.available()) selected.add(entry.id());
    }
    public void clearFiltered() {
        if (finished) return;
        // Snapshot first: deselecting must not shrink a selected-only iteration in progress.
        for (Entry entry : filtered()) selected.remove(entry.id());
        if (selectedOnly) cached = null;
    }
    public void clearAll() { if (!finished) { selected.clear(); if (selectedOnly) cached = null; } }
    public void apply(Consumer<Set<String>> consumer) {
        if (finished) return;
        finished = true;
        if (changed()) consumer.accept(snapshot());
    }
    public void cancel() { finished = true; }
}
