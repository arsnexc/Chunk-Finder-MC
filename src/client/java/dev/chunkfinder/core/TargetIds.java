package dev.chunkfinder.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Registry IDs, not registry default values. Unknown valid IDs remain saved but match nothing. */
public final class TargetIds {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private TargetIds() { }

    public static String normalize(String value) {
        if (value == null) return null;
        String id = value.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return null;
        if (!id.contains(":")) id = "minecraft:" + id;
        return ID.matcher(id).matches() ? id : null;
    }

    public static Set<String> copy(Collection<String> values) {
        TreeSet<String> result = new TreeSet<>();
        if (values != null) for (String value : values) {
            String id = normalize(value);
            if (id != null) result.add(id);
        }
        return Collections.unmodifiableSet(result);
    }
}
