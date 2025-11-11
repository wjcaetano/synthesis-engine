package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.tools.ToolItem;
import lombok.Data;

import java.util.List;

@Data
public class ChainRequestDto {
    private String resultSelection;
    private List<ToolItem> chain;
}
