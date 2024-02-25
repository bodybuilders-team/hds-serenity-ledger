package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CollapsingSet implements Set<Integer> {

    private final Set<Integer> set;
    private int floor = -1;

    public CollapsingSet() {
        this.set = new HashSet<>();
    }

    @Override
    public int size() {
        synchronized (this.set) {
            return this.floor + this.set.size();
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (this.set) {
           return (this.floor + this.set.size()) == 0;
        }
    }

    @Override
    public boolean contains(Object o) {
        synchronized (this.set) {
            if (!(o instanceof Integer i)) return false;
            return i <= this.floor || this.set.contains(i);
        }
    }

    private boolean unsafeContains(Object o) {
        if (!(o instanceof Integer i)) return false;
        return i <= this.floor || this.set.contains(i);
    }

    private Set<Integer> getFullSet() {
        // inefficient
        synchronized (this.set) {
            Set<Integer> s = new HashSet<>();
            if (this.floor > 0)
                s = IntStream.rangeClosed(1, floor).boxed().collect(Collectors.toCollection(HashSet::new));
            s.addAll(this.set);
            return s;
        }
    }

    @Override
    public Iterator<Integer> iterator() {
        return this.getFullSet().iterator();
    }

    @Override
    public Object[] toArray() {
        return this.getFullSet().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.getFullSet().toArray(a);
    }

    @Override
    public boolean add(Integer integer) {
        synchronized (this.set) {
            if (this.unsafeContains(integer)) return false;
            if (integer == (floor + 1)) {
                int newFloor = integer;
                boolean removeReturn = true;
                this.set.add(integer);
                while (this.set.contains(newFloor) && removeReturn) {
                    removeReturn = this.set.remove(newFloor);
                    this.floor = newFloor++;
                }
                return removeReturn;
            } else return this.set.add(integer);
        }
    }

    @Override
    public boolean remove(Object o) {
        // not used
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        // not used
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends Integer> c) {
        c.forEach(this::add);
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // not used
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        // not used
        return false;
    }

    @Override
    public void clear() {
        synchronized (this.set) {
            this.floor = 0;
            this.set.clear();
        }
    }
}
