package com.capco.brsp.synthesisengine.utils;

import java.util.Map;

public class ContextUtils {

    public static <T> T withTemporaryContext(
            Map<String, Object> context,
            Map<String, Object> temporaryValues,
            ContextAction<T> action) throws Exception {
        Map<String, Object> backup = new ConcurrentLinkedHashMap<>();
        for (String key : temporaryValues.keySet()) {
            backup.put(key, context.get(key));
        }

        try {
            context.putAll(temporaryValues);

            return action.execute();
        } finally {
            for (Map.Entry<String, Object> entry : backup.entrySet()) {
                if (entry.getValue() == null) {
                    context.remove(entry.getKey());
                } else {
                    context.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}

