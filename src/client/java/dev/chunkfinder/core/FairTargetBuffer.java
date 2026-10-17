package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Bounded nearest samples with a reserved presence for distinct targets and weighted spare slots. */
public final class FairTargetBuffer<T> {
    public record Entry<T>(T value,String target,double distance,long order){}
    private final class Group {
        final String id; final TargetPolicies.Rule rule;
        final PriorityQueue<Entry<T>> points=new PriorityQueue<>(Comparator.<Entry<T>>comparingDouble(Entry::distance).thenComparingLong(Entry::order).reversed());
        Group(String id){this.id=id;rule=policies.rule(id);}
        double load(){return (double)points.size()/(rule.priority()+1);}
    }
    private final int capacity;
    private final TargetPolicies.Snapshot policies;
    private final boolean display;
    private final Map<String,Group> groups=new HashMap<>();
    private int count;
    private long sequence;
    private Group largest;
    public FairTargetBuffer(int capacity,TargetPolicies.Snapshot policies,boolean display){
        if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;this.policies=policies;this.display=display;
    }
    public boolean accepts(String target,double distance) {
        if(target==null)target="";
        if(!Double.isFinite(distance) || distance<0)return false;
        TargetPolicies.Rule rule=policies.rule(target);
        if(rule.limit()==0 || display && rule.range()>0 && distance>(double)rule.range()*rule.range())return false;
        Group group=groups.get(target);int limit=display?Math.min(capacity,rule.limit()):capacity;
        if(group!=null && group.points.size()>=limit)return distance<group.points.peek().distance();
        if(count<capacity)return true;
        if(group==null)return largest!=null || groups.values().stream().anyMatch(g -> rule.priority()>g.rule.priority() || rule.priority()==g.rule.priority() && distance<g.points.peek().distance());
        return largest!=null && largest!=group && largest.load()-group.load()>1.0/(rule.priority()+1)+1e-9
                || distance<group.points.peek().distance();
    }
    public void offer(T value,String target,double distance){
        if(target==null)target="";
        if(!accepts(target,distance))return;
        TargetPolicies.Rule rule=policies.rule(target);
        if(rule.limit()==0 || display && rule.range()>0 && distance>(double)rule.range()*rule.range())return;
        Group group=groups.get(target);
        int perTarget=display?Math.min(capacity,rule.limit()):capacity;
        Entry<T> candidate=new Entry<>(value,target,distance,sequence++);
        if(group!=null && group.points.size()>=perTarget){replace(group,candidate);return;}
        if(count<capacity){if(group==null){group=new Group(target);groups.put(target,group);}add(group,candidate);return;}
        if(group==null){
            Group victim=largest;
            if(victim==null || victim.points.size()<=1){
                victim=groups.values().stream().min(Comparator.comparingInt((Group g)->g.rule.priority())
                        .thenComparing(Comparator.comparingDouble((Group g)->g.points.peek().distance()).reversed()).thenComparing(g->g.id)).orElseThrow();
                if(rule.priority()<victim.rule.priority() || rule.priority()==victim.rule.priority() && distance>=victim.points.peek().distance())return;
            }
            removeWorst(victim);group=new Group(target);groups.put(target,group);add(group,candidate);return;
        }
        Group victim=largest;
        if(victim!=null && victim!=group && victim.points.size()>1 && victim.load()-group.load()>1.0/(rule.priority()+1)+1e-9){
            removeWorst(victim);add(group,candidate);
        }else replace(group,candidate);
    }
    private void replace(Group group,Entry<T> candidate){
        if(!group.points.isEmpty() && candidate.distance()<group.points.peek().distance()){group.points.poll();group.points.add(candidate);}
    }
    private void add(Group group,Entry<T> candidate){group.points.add(candidate);count++;recompute();}
    private void removeWorst(Group group){group.points.poll();count--;if(group.points.isEmpty())groups.remove(group.id);}
    private void recompute(){largest=groups.values().stream().filter(g->g.points.size()>1).max(Comparator.comparingDouble(Group::load).thenComparing(g->g.id)).orElse(null);}
    public List<Entry<T>> entries(){
        List<Entry<T>> result=new ArrayList<>(count);for(Group group:groups.values())result.addAll(group.points);
        result.sort(Comparator.comparingDouble(Entry<T>::distance).thenComparingLong(Entry::order));return List.copyOf(result);
    }
    public List<T> values(){return entries().stream().map(Entry::value).toList();}
    public boolean removeIf(java.util.function.Predicate<T> predicate) {
        boolean changed=false;var iterator=groups.values().iterator();
        while(iterator.hasNext()) { Group group=iterator.next();int before=group.points.size();
            group.points.removeIf(entry->predicate.test(entry.value()));int removed=before-group.points.size();
            if(removed>0){count-=removed;changed=true;}if(group.points.isEmpty())iterator.remove();
        }
        if(changed)recompute();return changed;
    }
    public int size(){return count;}
}
