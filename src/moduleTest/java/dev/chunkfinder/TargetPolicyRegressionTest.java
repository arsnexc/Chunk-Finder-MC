package dev.chunkfinder;

import dev.chunkfinder.core.FairTargetBuffer;
import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.TargetPolicies;
import dev.chunkfinder.settings.TargetPolicyConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class TargetPolicyRegressionTest {
    private static int assertions;
    static int run() throws Exception {
        assertions=0;
        FairTargetBuffer<Integer> cache=new FairTargetBuffer<>(128,TargetPolicies.DEFAULT,false);
        for(int i=0;i<10000;i++)cache.offer(i,"test:common",i);
        cache.offer(-1,"test:rare",1000000);
        check(cache.size()==128 && cache.values().contains(-1),"a common block cannot evict the last sample of a rare selected target");
        var weighted=new TargetPolicies.Snapshot(TargetPolicies.DEFAULT_RULE,Map.of("test:rare",new TargetPolicies.Rule(5,0,48)));
        FairTargetBuffer<Integer> display=new FairTargetBuffer<>(12,weighted,true);
        for(int i=0;i<100;i++)display.offer(i,"test:common",i);
        for(int i=0;i<100;i++)display.offer(-i-1,"test:rare",100+i);
        long rare=display.entries().stream().filter(e->e.target().equals("test:rare")).count();
        check(rare>6 && rare<12,"priority receives more spare slots without erasing the other target");
        var limited=new TargetPolicies.Snapshot(TargetPolicies.DEFAULT_RULE,Map.of("test:one",new TargetPolicies.Rule(0,10,1),"test:off",new TargetPolicies.Rule(10,0,0)));
        FairTargetBuffer<Integer> limits=new FairTargetBuffer<>(48,limited,true);
        limits.offer(9,"test:one",81);limits.offer(1,"test:one",1);limits.offer(2,"test:one",4);limits.offer(100,"test:one",10000);limits.offer(0,"test:off",0);
        check(limits.values().equals(java.util.List.of(1)),"target distance/visible caps keep only the nearest eligible match and hide limit-zero targets");
        limits.offer(0,"test:other",Double.NaN);check(limits.size()==1,"invalid distances are ignored");

        class World implements LoadedBlockScanner.World {
            String target="";
            public int minY(){return 0;}public int maxY(){return 32;}
            public boolean loaded(int x,int z){return x==0 && z==0;}
            public boolean matches(int x,int y,int z){target=x==15&&y==31&&z==15?"test:rare":"test:common";return true;}
            public String lastMatchTarget(){return target;}
        }
        World world=new World();Object identity=new Object();LoadedBlockScanner scanner=new LoadedBlockScanner(256);
        for(int i=0;i<32;i++)scanner.tick(identity,world,8,0,8,0);
        var rarePoint=new LoadedBlockScanner.Point(15,31,15);
        check(scanner.points().size()==128 && scanner.points().contains(rarePoint),"production scanner retains rare metadata despite a dense common block");
        check(scanner.targetId(rarePoint).equals("test:rare"),"target identity is retained without extra color reads");
        var visible=scanner.nearestMatching(world,8.5,.5,8.5,48);
        check(visible.size()==48 && visible.contains(rarePoint),"fairness applies again at the render cap, not only the scan cache");
        scanner.blockChanged(15,31,15);scanner.tick(identity,world,8,0,8,0);
        check(scanner.points().size()<=128 && scanner.points().contains(rarePoint),"received-update overlays retain the same fair memory cap");
        Path root=Path.of("build/module-regression");Files.createDirectories(root);Path file=Files.createTempFile(root,"rules-",".json");
        try{
            Files.writeString(file,"{}");TargetPolicyConfig config=new TargetPolicyConfig(file);config.apply(weighted);
            check(new TargetPolicyConfig(file).snapshot().rule("test:rare").priority()==5,"target policies persist");
            var draft=new TargetPolicies.Draft(config.snapshot());draft.set("test:common",new TargetPolicies.Rule(99,999,999));
            check(draft.rule("test:common").equals(new TargetPolicies.Rule(10,512,48)),"editable policy bounds are enforced");
            check(config.snapshot().rule("test:common").equals(TargetPolicies.DEFAULT_RULE),"policy editing is transactional");
            draft.reset("test:common");check(draft.rule("test:common").equals(TargetPolicies.DEFAULT_RULE),"reset target inherits module defaults");
        }finally{Files.deleteIfExists(file);}
        return assertions;
    }
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
