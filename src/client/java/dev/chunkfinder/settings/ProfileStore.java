package dev.chunkfinder.settings;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Local client-settings profiles. Server authorization, game options and arbitrary paths are excluded. */
public class ProfileStore {
    public static final Set<String> OWNED=Set.of(
        "chunkfinder.json","chunkfinder-ui.json","item_esp.json","storage_esp.json","spawner_esp.json","ancient_debris_finder.json",
        "chunkfinder-entities.json","chunkfinder-dropped-items.json","chunkfinder-freecam.json","chunkfinder-target.json","chunkfinder-trajectories.json",
        "chunkfinder-colors-item_esp.json","chunkfinder-colors-storage_esp.json","chunkfinder-colors-spawner_esp.json","chunkfinder-colors-ancient_debris_finder.json",
        "chunkfinder-colors-entities.json","chunkfinder-colors-items.json","chunkfinder-colors-base-finder.json",
        "chunkfinder-rules-item_esp.json","chunkfinder-rules-storage_esp.json","chunkfinder-rules-spawner_esp.json","chunkfinder-rules-ancient_debris_finder.json",
        "chunkfinder-rules-entities.json","chunkfinder-rules-items.json");
    public record Snapshot(Map<String,String> files){public Snapshot{files=Map.copyOf(files);}}
    public record Change(String file,String action){ }
    private static final int MAX_FILE=1024*1024,MAX_PROFILE=20*1024*1024, MAX_TOTAL=8*1024*1024;
    private final Path root,profiles,backups;
    public ProfileStore(Path configDirectory){root=configDirectory.toAbsolutePath().normalize();profiles=root.resolve("chunkfinder-profiles");backups=root.resolve("chunkfinder-backups");}
    public static String name(String name) throws IOException {
        if(name==null || !name.matches("[a-zA-Z0-9_-]{1,48}"))throw new IOException("Use 1–48 letters, digits, hyphens or underscores, not a path");
        return name.toLowerCase(java.util.Locale.ROOT);
    }
    private static void safeDirectory(Path directory) throws IOException {
        if(Files.isSymbolicLink(directory))throw new IOException("Refusing a symbolic-link settings directory");
        Files.createDirectories(directory);
    }
    private Path owned(String file) throws IOException {
        if(!OWNED.contains(file))throw new IOException("Not a client setting owned by Chunk Finder: "+file);
        Path path=root.resolve(file);
        if(Files.isSymbolicLink(path) || Files.exists(path) && !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Refusing non-regular settings file: "+file);
        return path;
    }
    public Snapshot capture() throws IOException {
        safeDirectory(root);Map<String,String> data=new TreeMap<>();long total=0;
        for(String file:OWNED){Path path=owned(file);if(Files.exists(path)){long size=Files.size(path);if(size>MAX_FILE || (total+=size)>MAX_TOTAL)throw new IOException("Settings data too large: "+file);data.put(file,Files.readString(path));}}
        return new Snapshot(data);
    }
    public List<Change> preview(Snapshot target) throws IOException {
        validate(target);var current=capture();List<Change> changes=new ArrayList<>();
        for(String file:new java.util.TreeSet<>(OWNED)){
            String old=current.files.get(file),next=target.files.get(file);
            if(java.util.Objects.equals(old,next))continue;
            if(old!=null && next!=null)try{if(JsonParser.parseString(old).equals(JsonParser.parseString(next)))continue;}catch(RuntimeException ignored){}
            changes.add(new Change(file,next==null?"Reset to defaults":old==null?"Create":"Replace"));
        }
        return List.copyOf(changes);
    }
    public void save(String label,Snapshot snapshot) throws IOException {
        validate(snapshot);safeDirectory(profiles);Path destination=profiles.resolve(name(label)+".json");
        if(Files.exists(destination))throw new IOException("A profile with that name already exists; choose another name");
        writeAtomic(destination,encode(snapshot));
    }
    public List<String> names(boolean backup) throws IOException {
        Path directory=backup?backups:profiles;safeDirectory(directory);
        try(var paths=Files.list(directory)){
            return paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p))
                .map(p->p.getFileName().toString()).filter(s->s.matches("[a-z0-9_-]{1,80}\\.json"))
                .map(s->s.substring(0,s.length()-5)).sorted().toList();
        }
    }
    public Snapshot load(String label,boolean backup) throws IOException {
        Path directory=backup?backups:profiles;safeDirectory(directory);
        if(label==null||!label.matches("[a-z0-9_-]{1,80}"))throw new IOException("Invalid profile name");
        Path path=directory.resolve(label+".json");
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_PROFILE)throw new IOException("Invalid or oversized profile file");
        try{
            String text=Files.readString(path);checkDepth(text);JsonObject json=JsonParser.parseString(text).getAsJsonObject();
            if(!json.has("format")||!json.get("format").getAsString().equals("chunkfinder-client-profile-1"))throw new IOException("Unsupported profile format");
            JsonObject files=json.getAsJsonObject("files");if(files==null)throw new IOException("Profile has no settings");
            Map<String,String> values=new TreeMap<>();
            for(var entry:files.entrySet()){if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString())throw new IOException("Invalid embedded settings");values.put(entry.getKey(),entry.getValue().getAsString());}
            Snapshot snapshot=new Snapshot(values);validate(snapshot);return snapshot;
        }catch(RuntimeException exception){throw new IOException("Malformed profile",exception);}
    }
    public Path apply(Snapshot target) throws IOException {
        validate(target);Snapshot before=capture();safeDirectory(backups);
        Path backup=backups.resolve("backup-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+".json");
        writeAtomic(backup,encode(before)); // Backup must succeed before changing anything.
        List<String> touched=new ArrayList<>();
        try{
            for(String file:new java.util.TreeSet<>(OWNED)){
                if(java.util.Objects.equals(before.files.get(file),target.files.get(file)))continue;
                Path path=owned(file);touched.add(file);
                writeSetting(path,target.files.get(file));
            }
        }catch(IOException | RuntimeException failure){
            IOException error=new IOException("Profile apply failed; rollback attempted. Backup: "+backup.getFileName(),failure);
            for(String file:touched)try{restoreSetting(owned(file),before.files.get(file));}catch(IOException restore){error.addSuppressed(restore);}
            throw error;
        }
        return backup;
    }
    /** Overrideable for deterministic failure testing; production uses atomic local replacement. */
    protected void writeSetting(Path path,String text) throws IOException { restoreSetting(path,text); }
    private static void restoreSetting(Path path,String text) throws IOException {if(text==null)Files.deleteIfExists(path);else writeAtomic(path,text);}
    private static String encode(Snapshot snapshot){JsonObject o=new JsonObject();o.addProperty("format","chunkfinder-client-profile-1");JsonObject files=new JsonObject();new TreeMap<>(snapshot.files).forEach(files::addProperty);o.add("files",files);return new GsonBuilder().setPrettyPrinting().create().toJson(o);}
    private static void writeAtomic(Path path,String text) throws IOException {
        if(Files.isSymbolicLink(path))throw new IOException("Refusing symbolic-link destination");
        Path temp=Files.createTempFile(path.getParent(),"chunkfinder-",".tmp");
        try{Files.writeString(temp,text);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temp);}
    }
    private static void validate(Snapshot snapshot) throws IOException {
        int total=0;
        for(var entry:snapshot.files.entrySet()){
            if(!OWNED.contains(entry.getKey()))throw new IOException("Profile contains an unowned/server/path entry: "+entry.getKey());
            String text=entry.getValue();if(text==null || text.length()>MAX_FILE)throw new IOException("Oversized profile");int bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes>MAX_FILE || (total+=bytes)>MAX_TOTAL)throw new IOException("Oversized profile");checkDepth(text);
            try{var parsed=JsonParser.parseString(text);if(!parsed.isJsonObject())throw new IOException("Setting is not a JSON object: "+entry.getKey());validateSettings(entry.getKey(),parsed.getAsJsonObject());}
            catch(RuntimeException e){throw new IOException("Malformed setting: "+entry.getKey(),e);}
        }
    }
    private static void validateSettings(String file,JsonObject object) throws IOException {
        for(String key:Set.of("enabled","traces","blockHighlight","exposedOnly","option","swapStrafe","fullTerrain","horizontalFlight","hud","hudBackgrounds","hudDetails","reducedMotion","highContrast","tooltips","showGeodes","strictBaseClues","hideZeroRows","snapHud"))
            if(object.has(key) && (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()))throw new IOException("Expected boolean: "+file+":"+key);
        bounded(object,"searchRadius",2,12);bounded(object,"simulationDistance",2,12);bounded(object,"sensitivity",1,10);
        bounded(object,"accelerationTicks",0,30);bounded(object,"decelerationTicks",0,30);
        if(file.equals("chunkfinder-freecam.json"))bounded(object,"range",1,10);
        if(file.equals("chunkfinder-entities.json")||file.equals("chunkfinder-dropped-items.json"))bounded(object,"range",16,128);
        if(file.equals("chunkfinder-trajectories.json"))bounded(object,"range",12,72);
        if(file.equals("chunkfinder-target.json"))bounded(object,"range",8,64);
        bounded(object,"hudLines",2,10);bounded(object,"hudScale",60,140);bounded(object,"menuScale",75,150);
        bounded(object,"hudOpacity",0,90);bounded(object,"hudX",0,1000);bounded(object,"hudY",0,1000);
        for(String key:List.of("blockIds","entityIds","itemIds"))if(object.has(key)){
            if(!object.get(key).isJsonArray())throw new IOException("Expected target array: "+key);
            for(var value:object.getAsJsonArray(key))if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||dev.chunkfinder.core.TargetIds.normalize(value.getAsString())==null)throw new IOException("Invalid target ID in "+key);
        }
        for(String key:List.of("minimizedPanels","panelOrder","favorites","hiddenHudRows","pinnedHudRows"))if(object.has(key)){
            if(!object.get(key).isJsonArray())throw new IOException("Expected string list: "+key);
            for(var value:object.getAsJsonArray(key))if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().length()>100)throw new IOException("Invalid list entry: "+key);
        }
        if(file.startsWith("chunkfinder-colors-")){
            if(object.has("base") && !object.get("base").isJsonNull())color(object.get("base"));
            if(object.has("targets")){
                if(!object.get("targets").isJsonObject())throw new IOException("Expected color map");
                for(var e:object.getAsJsonObject("targets").entrySet()){
                    if(dev.chunkfinder.core.TargetIds.normalize(e.getKey())==null)throw new IOException("Invalid palette target");color(e.getValue());
                }
            }
        }
        if(file.startsWith("chunkfinder-rules-")){
            if(object.has("defaults")){if(!object.get("defaults").isJsonObject())throw new IOException("Invalid default rules");rule(object.getAsJsonObject("defaults"));}
            if(object.has("targets")){
                if(!object.get("targets").isJsonObject())throw new IOException("Expected rule map");
                for(var e:object.getAsJsonObject("targets").entrySet()){
                    if(dev.chunkfinder.core.TargetIds.normalize(e.getKey())==null||!e.getValue().isJsonObject())throw new IOException("Invalid rule target");rule(e.getValue().getAsJsonObject());
                }
            }
        }
    }
    private static void rule(JsonObject object) throws IOException {bounded(object,"priority",0,10);bounded(object,"range",0,512);bounded(object,"limit",0,48);}
    private static void color(com.google.gson.JsonElement value) throws IOException {if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||dev.chunkfinder.core.EspPalette.parse(value.getAsString()).isEmpty())throw new IOException("Invalid RGB color");}
    private static void bounded(JsonObject object,String key,int min,int max) throws IOException {
        if(!object.has(key))return;var value=object.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IOException("Expected integer: "+key);
        try{var number=value.getAsBigDecimal();if(number.stripTrailingZeros().scale()>0 || number.compareTo(java.math.BigDecimal.valueOf(min))<0 || number.compareTo(java.math.BigDecimal.valueOf(max))>0)throw new IOException("Out-of-range setting: "+key);}
        catch(NumberFormatException e){throw new IOException("Invalid number: "+key,e);}
    }
    private static void checkDepth(String text) throws IOException {
        int depth=0;boolean quote=false,escape=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')quote=false;}
            else if(c=='\"')quote=true;else if(c=='{'||c=='['){if(++depth>32)throw new IOException("JSON nesting is too deep");}else if(c=='}'||c==']')depth--;
        }
    }
}
