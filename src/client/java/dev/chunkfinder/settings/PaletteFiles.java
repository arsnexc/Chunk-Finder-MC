package dev.chunkfinder.settings;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chunkfinder.core.EspPalette;
import dev.chunkfinder.core.TargetIds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Local-only palette interchange. Imports never touch target selection or module enable state. */
public final class PaletteFiles {
    private final Path root;
    public PaletteFiles(Path config){root=config.toAbsolutePath().normalize().resolve("chunkfinder-palettes");}
    private void directory()throws IOException{if(Files.isSymbolicLink(root))throw new IOException("Palette directory cannot be a symbolic link");Files.createDirectories(root);}
    public List<String> names()throws IOException{directory();try(var files=Files.list(root)){return files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p)).map(p->p.getFileName().toString()).filter(n->n.matches("[a-z0-9_-]{1,48}\\.json")).map(n->n.substring(0,n.length()-5)).sorted().toList();}}
    public void save(String name,EspPalette.Snapshot value)throws IOException{
        directory();Path path=root.resolve(ProfileStore.name(name)+".json");if(Files.exists(path))throw new IOException("Palette already exists; choose another name");
        String encoded=encode(value);if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024*1024)throw new IOException("Palette too large");
        Path temporary=Files.createTempFile(root,"palette-",".tmp");
        try{Files.writeString(temporary,encoded);try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,path);}}finally{Files.deleteIfExists(temporary);}
    }
    public EspPalette.Snapshot load(String name)throws IOException{
        directory();Path path=root.resolve(ProfileStore.name(name)+".json");
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>1024*1024)throw new IOException("Invalid palette file");
        return decode(Files.readString(path));
    }
    public static String encode(EspPalette.Snapshot value){
        JsonObject root=new JsonObject();root.addProperty("format","chunkfinder-palette-1");if(value.base()!=null)root.addProperty("base",EspPalette.hex(value.base()));
        JsonObject targets=new JsonObject();value.targets().forEach((id,color)->targets.addProperty(id,EspPalette.hex(color)));root.add("targets",targets);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
    public static EspPalette.Snapshot decode(String source)throws IOException{
        if(source==null || source.length()>1024*1024)throw new IOException("Palette too large");
        // Interchange schema is shallow. Reject pathological nesting before JSON parsing.
        int depth=0;boolean quote=false,escape=false;
        for(char c:source.toCharArray()){if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')quote=false;}else if(c=='\"')quote=true;else if(c=='{'||c=='['){if(++depth>8)throw new IOException("Palette nesting too deep");}else if(c=='}'||c==']')depth--;}
        try{
            JsonObject root=JsonParser.parseString(source).getAsJsonObject();
            if(!root.has("format")||!root.get("format").getAsString().equals("chunkfinder-palette-1"))throw new IOException("Unsupported palette format");
            Integer base=root.has("base")?color(root.get("base")):null;
            JsonObject entries=root.getAsJsonObject("targets");if(entries==null || entries.size()>4096)throw new IOException("Invalid palette target map");
            Map<String,Integer> targets=new TreeMap<>();for(var e:entries.entrySet()){
                String id=TargetIds.normalize(e.getKey());if(id==null)throw new IOException("Invalid palette target ID");targets.put(id,color(e.getValue()));
            }
            return new EspPalette.Snapshot(base,targets);
        }catch(RuntimeException e){throw new IOException("Malformed palette",e);}
    }
    private static int color(com.google.gson.JsonElement value)throws IOException{
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IOException("RGB color must be a hex string");
        var parsed=EspPalette.parse(value.getAsString());if(parsed.isEmpty())throw new IOException("Invalid RGB color");return parsed.getAsInt();
    }
}
