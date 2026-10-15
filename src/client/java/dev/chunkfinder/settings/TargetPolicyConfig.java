package dev.chunkfinder.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.chunkfinder.core.TargetPolicies;
import java.nio.file.Path;
import java.util.TreeMap;

public final class TargetPolicyConfig {
    private final JsonConfig config;
    private TargetPolicies.Snapshot snapshot;
    public TargetPolicyConfig(Path path){
        config=new JsonConfig(path);TreeMap<String,TargetPolicies.Rule> targets=new TreeMap<>();
        JsonElement entries=config.element("targets");
        if(entries.isJsonObject())entries.getAsJsonObject().entrySet().forEach(e->{if(e.getValue().isJsonObject())targets.put(e.getKey(),read(e.getValue()));});
        snapshot=new TargetPolicies.Snapshot(read(config.element("defaults")),targets);
    }
    private static TargetPolicies.Rule read(JsonElement value){
        if(!value.isJsonObject())return TargetPolicies.DEFAULT_RULE;
        JsonObject o=value.getAsJsonObject();return new TargetPolicies.Rule(integer(o,"priority",0),integer(o,"range",0),integer(o,"limit",48));
    }
    private static int integer(JsonObject o,String key,int fallback){
        try{var v=o.get(key);return v!=null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()?v.getAsInt():fallback;}catch(RuntimeException e){return fallback;}
    }
    private static JsonObject encode(TargetPolicies.Rule rule){JsonObject o=new JsonObject();o.addProperty("priority",rule.priority());o.addProperty("range",rule.range());o.addProperty("limit",rule.limit());return o;}
    public TargetPolicies.Snapshot snapshot(){return snapshot;}
    public void apply(TargetPolicies.Snapshot value){
        if(snapshot.equals(value))return;snapshot=value;
        config.putElement("defaults",encode(value.defaults()));JsonObject map=new JsonObject();value.targets().forEach((id,rule)->map.add(id,encode(rule)));
        config.putElement("targets",map);config.save();
    }
}
