package com.capco.brsp.synthesisengine.utils;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLinkedList<E> extends LinkedList<E> {
    private final Lock lock = new ReentrantLock();

    public ConcurrentLinkedList() {
        super();
    }

    public ConcurrentLinkedList(Collection<E> copy) {
        super();
        if (copy != null) {
            this.addAll(copy);
        }
    }

    public static <E> ConcurrentLinkedList<E> createInstance() {
        return new ConcurrentLinkedList<>();
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
    public E get(int index) {
        lock.lock();
        try {
            return super.get(index);
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
    public void clear() {
        lock.lock();
        try {
            super.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        lock.lock();
        try {
            return super.addAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ConcurrentLinkedList<?> other)) {
            return false;
        }
        lock.lock();
        try {
            return super.equals(other);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int hashCode() {
        lock.lock();
        try {
            return super.hashCode();
        } finally {
            lock.unlock();
        }
    }

    public List<E> createSnapshot() {
        lock.lock();
        try {
            return new LinkedList<>(this);
        } finally {
            lock.unlock();
        }
    }
}
