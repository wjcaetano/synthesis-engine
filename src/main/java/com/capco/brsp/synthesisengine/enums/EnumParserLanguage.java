package com.capco.brsp.synthesisengine.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum EnumParserLanguage {
    CUSTOM("custom"),
    DB2("db2"),
    COBOL("cobol"),
    JAVA("java"),
    MARKDOWN("markdown"),
    NATURAL("natural"),
    NATURAL_MAPS("natural_maps"),
    PYTHON("python"),
    CSHARP("csharp", "C#");

    private final List<String> keys;

    EnumParserLanguage(String... keys) {
        this.keys = List.of(keys);
    }

    public static EnumParserLanguage fromKeyIgnoreCase(String input) {
        return Arrays.stream(EnumParserLanguage.values())
                .filter(lang -> lang.getKeys().stream().anyMatch(k -> k.equalsIgnoreCase(input)))
                .findFirst()
                .orElse(EnumParserLanguage.CUSTOM);
    }
}
