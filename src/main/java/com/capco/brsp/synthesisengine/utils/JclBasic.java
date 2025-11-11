package com.capco.brsp.synthesisengine.utils;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class JclBasic {
    private String name;
    private String type;
    private String rawCode;
    private Set<String> execs;
    private Set<String> instreamRuns;
    private Set<String> ctcs;
}
