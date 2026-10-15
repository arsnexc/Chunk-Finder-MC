package dev.chunkfinder.core;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Per-target display rules. Range 0 inherits the module; limit 0 explicitly hides the target. */
public final class TargetPolicies {
    public record Rule(int priority, int range, int limit) {
        public Rule { priority=Math.max(0,Math.min(10,priority));range=Math.max(0,Math.min(512,range));limit=Math.max(0,Math.min(48,limit)); }
    }
    public static final Rule DEFAULT_RULE=new Rule(0,0,48);
    public static final Snapshot DEFAULT=new Snapshot(DEFAULT_RULE,Map.of());
    public record Snapshot(Rule defaults, Map<String,Rule> targets) {
        public Snapshot {
            defaults=defaults==null?DEFAULT_RULE:defaults;
            TreeMap<String,Rule> clean=new TreeMap<>();
            if(targets!=null)targets.forEach((id,rule)->{String key=TargetIds.normalize(id);if(key!=null && rule!=null)clean.put(key,rule);});
            targets=Collections.unmodifiableMap(clean);
        }
        public Rule rule(String id){return targets.getOrDefault(id==null?"":id,defaults);}
    }
    public static final class Draft {
        private Rule defaults;
        private final Map<String,Rule> targets;
        public Draft(Snapshot snapshot){defaults=snapshot.defaults;targets=new TreeMap<>(snapshot.targets);}
        public Rule rule(String id){return id.isEmpty()?defaults:targets.getOrDefault(id,defaults);}
        public void set(String id,Rule rule){if(id.isEmpty())defaults=rule;else {String key=TargetIds.normalize(id);if(key!=null)targets.put(key,rule);}}
        public void reset(String id){if(id.isEmpty())defaults=DEFAULT_RULE;else targets.remove(id);}
        public Snapshot snapshot(){return new Snapshot(defaults,targets);}
    }
    private TargetPolicies(){}
}
