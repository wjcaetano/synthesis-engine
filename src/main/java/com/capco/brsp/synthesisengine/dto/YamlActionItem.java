package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.enums.EnumYamlActionItemType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class YamlActionItem {
    private String targetPath;
    private EnumYamlActionItemType action;
    private Integer firstLine;
    private Integer lastLine;
    private String content;
    private String reason;
}
