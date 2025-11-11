package com.capco.brsp.synthesisengine.dto;

import atr.TreeNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.proleap.cobol.asg.metamodel.ASGElement;
import lombok.Builder;
import lombok.Data;
import org.antlr.v4.runtime.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ParsedObjects implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private transient CodePointCharStream charStream;
    @JsonIgnore
    private transient Lexer lexer;
    @JsonIgnore
    private transient CommonTokenStream tokens;
    @JsonIgnore
    private transient Parser parser;
    @JsonIgnore
    private transient ParserRuleContext parserRuleContext;
    @JsonIgnore
    private transient ASGElement asg;
    @JsonIgnore
    private transient TreeNode treeNode;
    @JsonIgnore
    private transient Map<String, List<ParserRuleContext>> visitedRules;

    @JsonIgnore
    private ObjectNode jsonTree;

    private String jsonString;
    private Object jsonObject;
}
