package com.capco.brsp.synthesisengine.dto.grammars;


import com.capco.brsp.synthesisengine.enums.EnumParserLanguage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import org.antlr.v4.tool.LexerGrammar;

@Builder
@Data
public class Grammar {
    private EnumParserLanguage enumParserLanguage;
    private final String name;
    private final String lexerGrammar;
    private final String parserGrammar;
    private final String entryRule;
    private final GrammarEngine engine;

//    @JsonIgnore
//    private final LexerGrammar antlr4LexerGrammar;
//    @JsonIgnore
//    private final org.antlr.v4.tool.Grammar antlr4Grammar;

    @JsonIgnore
    private final Class<?> lexerClass;
    @JsonIgnore
    private final Class<?> parserClass;
    @JsonIgnore
    private final ClassLoader classLoader;
}
