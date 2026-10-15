package dev.chunkfinder.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Live UI bindings, not a second copy of module settings. Values are saved by existing setters. */
public final class ClickGuiModel {
    private ClickGuiModel() { }

    public enum Icon { WORLD, MOVEMENT, RENDER, PLAYER }

    public record Category(String id, String label, Icon icon, List<Entry> entries) {
        public Category { entries = List.copyOf(entries); }
    }

    public record Entry(String id, String label, String hint, BooleanSupplier enabled, Runnable toggle,
                        List<Setting> settings, boolean action) {
        public Entry(String id,String label,String hint,BooleanSupplier enabled,Runnable toggle,List<Setting> settings){this(id,label,hint,enabled,toggle,settings,false);}
        public Entry { settings = List.copyOf(settings); }
    }

    public sealed interface Setting permits Toggle, Slider, Action {
        String id();
        String label();
        String hint();
        int height();
    }

    public record Toggle(String id, String label, String hint, BooleanSupplier enabled,
                         Runnable toggle) implements Setting {
        @Override public int height() { return 20; }
    }

    public record Slider(String id, String label, String hint, IntSupplier value, int min, int max,
                         IntConsumer update, IntFunction<String> format) implements Setting {
        public Slider {
            if (max <= min) throw new IllegalArgumentException("Slider range must be positive");
        }
        @Override public int height() { return 31; }
        public void set(int requested) {
            int next = UiGeometry.clamp(requested, min, max);
            // Avoid repeated config writes when dragging within the same integer step.
            if (next != value.getAsInt()) update.accept(next);
        }
        public String displayValue() { return format.apply(value.getAsInt()); }
    }

    public record Action(String id, String label, String hint, Supplier<String> detail,
                         Runnable run) implements Setting {
        @Override public int height() { return detail == null ? 22 : 32; }
    }
}
