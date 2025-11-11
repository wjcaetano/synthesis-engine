package com.capco.brsp.synthesisengine.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnumYamlActionItemType {
    INCLUDE("INCLUDE"),
    REMOVE("REMOVE"),
    REPLACE("REPLACE");

    private final String key;
}
