package com.capco.brsp.synthesisengine.utils;

@FunctionalInterface
public interface ContextAction<T> {
    T execute() throws Exception;
}
