package com.capco.brsp.synthesisengine.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnumLLMGatewayRoles {
    USER("user"),
    SYSTEM("system");

    private final String key;
}
