package com.capco.brsp.synthesisengine.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    private final Lock lock = new ReentrantLock();

    public ConcurrentLinkedHashMap() {
        super();
    }

    public ConcurrentLinkedHashMap(Map<K, V> copy) {
        super();
        this.putAll(copy);
    }

    public static <K, V> ConcurrentLinkedHashMap<K, V> createInstance() {
        return new ConcurrentLinkedHashMap<>();
    }

    @Override
    public V put(K key, V value) {
        lock.lock();
        try {
            return super.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V get(Object key) {
        lock.lock();
        try {
            return super.get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V remove(Object key) {
        lock.lock();
        try {
            return super.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        lock.lock();
        try {
            super.putAll(m);
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
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ConcurrentLinkedHashMap<?, ?> other)) {
            return false;
        }
        lock.lock();
        other.lock.lock();
        try {
            return super.equals(other);
        } finally {
            lock.unlock();
            other.lock.unlock();
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

    public Map<K, V> createSnapshot() {
        lock.lock();
        try {
            return new ConcurrentLinkedHashMap<>(this);
        } finally {
            lock.unlock();
        }
    }
}

