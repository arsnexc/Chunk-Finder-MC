package dev.chunkfinder;

import dev.chunkfinder.core.EspPalette;
import dev.chunkfinder.core.PalettePresets;
import dev.chunkfinder.settings.PaletteFiles;
import dev.chunkfinder.settings.ProfileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

final class ProfilePaletteRegressionTest {
    private static int assertions;
    static int run()throws Exception{
        assertions=0;Path root=Path.of("build/module-regression");Files.createDirectories(root);Path dir=Files.createTempDirectory(root,"profiles-");
        try{
            Files.writeString(dir.resolve("chunkfinder.json"),"{\"enabled\":true,\"sensitivity\":7,\"simulationDistance\":5,\"other\":17}");
            Files.writeString(dir.resolve("item_esp.json"),"{\"blockIds\":[\"minecraft:diamond_ore\"],\"enabled\":true}");
            Files.writeString(dir.resolve("chunkfinder-server.json"),"{\"enabled\":true}");
            Files.writeString(dir.resolve("other-mod.json"),"{\"private\":42}");
            ProfileStore store=new ProfileStore(dir);var original=store.capture();
            check(!original.files().containsKey("chunkfinder-server.json")&&!original.files().containsKey("other-mod.json"),"profiles exclude server policy and unrelated files");
            store.save("Test_Profile",original);check(store.names(false).equals(java.util.List.of("test_profile")),"safe local profile names and enumeration");
            check(store.load("test_profile",false).equals(original),"profile export/import preserves exact owned file data");
            check(store.preview(original).isEmpty(),"unchanged profile preview is empty");
            var target=new ProfileStore.Snapshot(Map.of("item_esp.json","{\"blockIds\":[],\"enabled\":false}"));
            check(store.preview(target).size()==2,"preview distinguishes replacements and resets");
            Path backup=store.apply(target);
            check(Files.exists(backup)&&!Files.exists(dir.resolve("chunkfinder.json")),"backup precedes reset of absent owned settings");
            check(Files.readString(dir.resolve("chunkfinder-server.json")).equals("{\"enabled\":true}"),"applying a client profile never rewrites server authorization");
            String backupName=backup.getFileName().toString().replace(".json","");store.apply(store.load(backupName,true));
            check(store.capture().equals(original),"backup restoration restores all owned settings");
            ProfileStore faulty=new ProfileStore(dir){int writes;@Override protected void writeSetting(Path path,String text)throws IOException{if(++writes==2)throw new IOException("injected second-file failure");super.writeSetting(path,text);}};
            try{faulty.apply(target);throw new AssertionError("expected failure");}catch(IOException expected){assertions++;}
            check(store.capture().equals(original),"mid-transaction I/O failure rolls back already-written files");
            for(var bad:java.util.List.of(new ProfileStore.Snapshot(Map.of("../outside.json","{}")),new ProfileStore.Snapshot(Map.of("chunkfinder-server.json","{}")),new ProfileStore.Snapshot(Map.of("item_esp.json","[]")),new ProfileStore.Snapshot(Map.of("chunkfinder-freecam.json","{\"range\":999}")),new ProfileStore.Snapshot(Map.of("item_esp.json","{\"enabled\":\"true\"}")))){
                try{store.apply(bad);throw new AssertionError("bad profile accepted");}catch(IOException expected){assertions++;}
            }
            check(store.capture().equals(original),"invalid profiles leave current settings untouched");
            for(String name:new String[]{"../bad","a/b","", "x.json"})try{store.save(name,original);throw new AssertionError("path accepted");}catch(IOException expected){assertions++;}
            Path outside=dir.resolve("outside.json");Files.writeString(outside,"{\"private\":1}");
            Path linked=dir.resolve("chunkfinder-ui.json");Files.createSymbolicLink(linked,outside.toAbsolutePath());
            try{store.capture();throw new AssertionError("symlink read");}catch(IOException expected){assertions++;}Files.delete(linked);
            check(Files.readString(outside).equals("{\"private\":1}"),"symlink target is untouched");
            store.apply(new ProfileStore.Snapshot(Map.of()));check(store.capture().files().isEmpty(),"restore defaults deletes only owned client files after backup");
            check(Files.exists(dir.resolve("other-mod.json")),"restore defaults preserves other mods");

            PaletteFiles palettes=new PaletteFiles(dir);var palette=new EspPalette.Snapshot(0xFF123456,Map.of("minecraft:stone",0xFF112233,"missing:ore",0xFF445566));
            palettes.save("example",palette);check(palettes.load("example").equals(palette),"local palette roundtrip retains inherited/unknown target colors");
            for(String bad:java.util.List.of("{}","{\"format\":\"chunkfinder-palette-1\",\"targets\":{\"bad id\":\"#FFFFFF\"}}","{\"format\":\"chunkfinder-palette-1\",\"base\":\"#bad\",\"targets\":{}}"))try{PaletteFiles.decode(bad);throw new AssertionError("malformed palette");}catch(IOException expected){assertions++;}
            var preset=PalettePresets.apply("okabe-ito",palette,Set.of("minecraft:stone","minecraft:diamond_ore"));
            check(preset.targets().get("minecraft:stone").intValue()!=preset.targets().get("minecraft:diamond_ore").intValue(),"preset distinguishes selected targets");
            check(preset.targets().get("missing:ore").equals(palette.targets().get("missing:ore")),"preset preserves unrelated/missing-mod overrides");
            EspPalette.Draft draft=new EspPalette.Draft(palette);draft.replace(preset);check(draft.changed()&&palette.base()==0xFF123456,"import/preset changes a draft, not live palette state");
            try{palettes.save("../escape",palette);throw new AssertionError("palette traversal");}catch(IOException expected){assertions++;}
        }finally{try(var paths=Files.walk(dir)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        return assertions;
    }
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
