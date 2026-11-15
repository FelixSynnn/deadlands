package de.maxhenkel.sound_physics_remastered.configbuilder.custom;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public abstract class AbstractValueList<T> implements List<T> {
    protected final List<T> list;

    protected AbstractValueList(T... values) {
        this(Arrays.asList(values));
    }

    protected AbstractValueList(List<T> values) {
        this.list = Collections.unmodifiableList(values);
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.list.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return this.list.iterator();
    }

    @Override
    public Object[] toArray() {
        return this.list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return (T[])this.list.toArray(a);
    }

    @Override
    public boolean add(T s) {
        return throwException();
    }

    @Override
    public boolean remove(Object o) {
        return throwException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return this.list.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return throwException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        return throwException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return throwException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return throwException();
    }

    @Override
    public void clear() {
        throwException();
    }

    @Override
    public T get(int index) {
        return this.list.get(index);
    }

    @Override
    public T set(int index, T element) {
        return throwException();
    }

    @Override
    public void add(int index, T element) {
        throwException();
    }

    @Override
    public T remove(int index) {
        return throwException();
    }

    @Override
    public int indexOf(Object o) {
        return this.list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.list.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return this.list.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return this.list.listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return this.list.subList(fromIndex, toIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractValueList<?> that = (AbstractValueList<?>)o;
            return Objects.equals(this.list, that.list);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.list.hashCode();
    }

    private static <T> T throwException() {
        throw new UnsupportedOperationException("Can't modify config entries");
    }
}
