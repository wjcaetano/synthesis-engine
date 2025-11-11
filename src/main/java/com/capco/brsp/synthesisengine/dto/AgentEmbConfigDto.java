package com.capco.brsp.synthesisengine.dto;



import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
public class AgentEmbConfigDto {
    private String provider = "bedrock-cohere";
    private String user;
    private List<String> input;
    private String deploymentName;
    private String inputType;
    private Integer dimensions;
    private String encodingFormat;
}
