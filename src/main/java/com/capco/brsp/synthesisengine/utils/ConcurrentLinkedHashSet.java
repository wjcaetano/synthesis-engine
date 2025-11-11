package com.capco.brsp.synthesisengine.utils;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLinkedHashSet<E> extends LinkedHashSet<E> {
    private final Lock lock = new ReentrantLock();

    public ConcurrentLinkedHashSet() {
        super();
    }

    public ConcurrentLinkedHashSet(Set<E> copy) {
        super();
        this.addAll(copy);
    }

    public static <E> ConcurrentLinkedHashSet<E> createInstance() {
        return new ConcurrentLinkedHashSet<>();
    }

    @Override
    public boolean add(E e) {
        lock.lock();
        try {
            return super.add(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        lock.lock();
        try {
            return super.remove(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        lock.lock();
        try {
            return super.contains(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            super.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(java.util.Collection<? extends E> c) {
        lock.lock();
        try {
            return super.addAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeAll(java.util.Collection<?> c) {
        lock.lock();
        try {
            return super.removeAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean retainAll(java.util.Collection<?> c) {
        lock.lock();
        try {
            return super.retainAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return super.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return super.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<E> iterator() {
        return super.iterator();
    }

    public Set<E> createSnapshot() {
        lock.lock();
        try {
            return new ConcurrentLinkedHashSet<>(this);
        } finally {
            lock.unlock();
        }
    }
}
