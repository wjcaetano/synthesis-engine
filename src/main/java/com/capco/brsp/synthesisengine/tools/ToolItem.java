package com.capco.brsp.synthesisengine.tools;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class ToolItem {
    private String tool;
    private Map<String, Object> parameters;
    private String outType;

    private Map<String, Object> extraFields = new ConcurrentLinkedHashMap<>();

    @JsonAnySetter
    public void addExtraField(String name, Object value) {
        extraFields.put(name, value);
    }
}
