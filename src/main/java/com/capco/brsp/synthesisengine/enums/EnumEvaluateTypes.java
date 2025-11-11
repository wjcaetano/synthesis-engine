package com.capco.brsp.synthesisengine.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnumEvaluateTypes {
    FREEMARKER("freemarker"),
    GROOVY("groovy"),
    PROMPT("prompt"),
    SPEL("spel"),
    TRANSFORM("transform");

    private final String key;
}
