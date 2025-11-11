package com.capco.brsp.synthesisengine.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TaskMap {
    private final String name;
    private final Runnable runnable;
}
