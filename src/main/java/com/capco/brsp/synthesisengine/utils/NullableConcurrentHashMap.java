package com.capco.brsp.synthesisengine.utils;

import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NullableConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    private static final Object NULL_SENTINEL = new Object();

    @Override
    public V put(@NonNull K key, V value) {
        if (value == null) {
            return super.put(key, (V) NULL_SENTINEL);
        } else {
            return super.put(key, value);
        }
    }

    @Override
    public V get(Object key) {
        V value = super.get(key);
        return (value == NULL_SENTINEL) ? null : value;
    }

    @Override
    public V remove(@NonNull Object key) {
        V value = super.remove(key);
        return (value == NULL_SENTINEL) ? null : value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
