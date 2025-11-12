package com.capco.brsp.synthesisengine.utils;

import antlr4.*;
import atr.TreeNode;
import atr.TreeRewriter;
import com.capco.brsp.synthesisengine.dto.ParsedObjects;
import com.capco.brsp.synthesisengine.dto.grammars.Grammar;
import com.capco.brsp.synthesisengine.dto.grammars.GrammarEngine;
import com.capco.brsp.synthesisengine.enums.EnumParserLanguage;
import com.capco.brsp.synthesisengine.listeners.DescriptiveErrorListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.util.logging.Slf4j;
import io.proleap.cobol.Cobol85Lexer;
import io.proleap.cobol.Cobol85Parser;
import io.proleap.cobol.asg.metamodel.Program;
import io.proleap.cobol.asg.metamodel.impl.ProgramImpl;
import io.proleap.cobol.asg.params.CobolParserParams;
import io.proleap.cobol.asg.params.impl.CobolParserParamsImpl;
import io.proleap.cobol.asg.runner.impl.CobolParserRunnerImpl;
import io.proleap.cobol.asg.visitor.ParserVisitor;
import io.proleap.cobol.asg.visitor.impl.CobolCompilationUnitVisitorImpl;
import io.proleap.cobol.preprocessor.CobolPreprocessor;
import io.proleap.cobol.preprocessor.impl.CobolPreprocessorImpl;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@lombok.extern.slf4j.Slf4j
@Slf4j
public class ParserUtils {
    private static final Map<String, Grammar> GRAMMARS = new ConcurrentLinkedHashMap<>();
    private static final ParserUtils INSTANCE = new ParserUtils();

    private ParserUtils() {
    }

    public static ParserUtils getInstance() {
        return INSTANCE;
    }

//    public static String entrypointName(Grammar grammar) {
//        return grammar.getAntlr4Grammar().getRule(0).name;
//    }
//
//    public static List<String> listTokens(CommonTokenStream tokens, Grammar grammar) {
//        List<String> list = new ConcurrentLinkedList<>();
//
//        tokens.fill();
//        for (int i = 0; i < tokens.size(); i++) {
//            Token t = tokens.get(i);
//            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
//                list.add(String.format("%-20s type=%d text=%s%n", grammar.getAntlr4LexerGrammar().getVocabulary().getSymbolicName(t.getType()), t.getType(), t.getText()));
//            }
//        }
//
//        return list;
//    }
//
//    public static List<String> listTokenTypes(Grammar grammar) {
//        List<String> list = new ConcurrentLinkedList<>();
//
//        var vocab = grammar.getAntlr4Grammar().getVocabulary();
//        for (int i = 1; i <= vocab.getMaxTokenType(); i++) {
//            list.add(i + ": " + vocab.getSymbolicName(i));
//        }
//
//        return list;
//    }

    public static Grammar prepareGrammar(String languageName,
                                         GrammarEngine type,
                                         String languageLexer,
                                         String languageParser,
                                         String entryRule,
                                         Map<String, String> dependencies) {
        var languageKey = Utils.hashString(languageName, type.toString(), languageLexer, languageParser, entryRule);
        if (GRAMMARS.get(languageKey) instanceof Grammar cached) {
            return cached;
        }

        AntlrUtils.LoadedGrammars loaded = AntlrUtils.load(languageLexer, languageParser, dependencies);

        Grammar grammar = Grammar.builder()
                .enumParserLanguage(EnumParserLanguage.CUSTOM)
                .name(languageName)
                .lexerGrammar(languageLexer)
                .parserGrammar(languageParser)
                .entryRule(entryRule)
                .engine(type)
//                .antlr4LexerGrammar(antlr4LexerGrammar)
//                .antlr4Grammar(antlr4Grammar)
                .lexerClass(loaded.lexerClass)
                .parserClass(loaded.parserClass)
                .classLoader(loaded.classLoader)
                .build();

        GRAMMARS.put(languageKey, grammar);
        return grammar;
    }

    public static ParsedObjects parse(Map<String, Grammar> grammars, String languageKey, String content, String... findRules) throws JsonProcessingException, org.antlr.runtime.RecognitionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        EnumParserLanguage language = EnumParserLanguage.fromKeyIgnoreCase(languageKey);

        Grammar grammar = !Utils.isEmpty(grammars) ? grammars.get(languageKey) : null;
        if (language == EnumParserLanguage.CUSTOM) {
            if (grammar == null) {
                throw new IllegalStateException("Didn't found any language for the languageKey: " + languageKey);
            }
        } else {
            grammar = Grammar.builder().enumParserLanguage(language).build();
        }

        return parse(grammar, content, findRules);
    }

    public static ParsedObjects parse(Grammar grammar, String content, String... findRules) throws JsonProcessingException, org.antlr.runtime.RecognitionException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var charStream = CharStreams.fromString(content);

        var language = grammar.getEnumParserLanguage();
        if (language == EnumParserLanguage.MARKDOWN) {
            var listOfMarkdownCodes = Utils.extractMarkdownCodes(content);

            var markdownCodesListString = JsonUtils.writeAsJsonString(listOfMarkdownCodes, true);

            return ParsedObjects.builder()
                    .charStream(null)
                    .lexer(null)
                    .tokens(null)
                    .parser(null)
                    .parserRuleContext(null)
                    .visitedRules(new ConcurrentLinkedHashMap<>())
                    .jsonTree(null)
                    .jsonString(markdownCodesListString)
                    .jsonObject(listOfMarkdownCodes)
                    .build();
        } else if (language == EnumParserLanguage.COBOL) {
            CobolParserParams cobolParserParams = new CobolParserParamsImpl();
//            parserParams.setCopyBookFiles(listOfCopyBookFilesFiles);
            content = new CobolPreprocessorImpl().process(content, CobolPreprocessor.CobolSourceFormatEnum.FIXED, cobolParserParams);
        }

        var descriptiveErrorListener = DescriptiveErrorListener.getInstance();
        descriptiveErrorListener.setThrowException(true);

        Lexer lexer = switch (language) {
            case DB2 -> new DB2zSQLLexer(charStream);
            case COBOL -> new Cobol85Lexer(CharStreams.fromString(content));
            case JAVA -> new JavaLexer(charStream);
            case NATURAL -> new AdabasNaturalLexer(charStream);
            case NATURAL_MAPS -> new AdabasNaturalMapLexer(charStream);
            case PYTHON -> throw new UnsupportedOperationException("Python parsing is not implemented yet.");
            case CSHARP -> throw new UnsupportedOperationException("C# parsing is not implemented yet.");
            case CUSTOM -> (Lexer) grammar.getLexerClass().getConstructor(CharStream.class).newInstance(charStream);
            default -> throw new IllegalStateException("Unexpected value: " + language);
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(descriptiveErrorListener);

        var tokens = new CommonTokenStream(Objects.requireNonNull(lexer));

        var parsed = ParsedObjects.builder()
                .charStream(charStream)
                .lexer(lexer)
                .tokens(tokens)
                .visitedRules(new ConcurrentLinkedHashMap<>())
                .build();

        switch (language) {
            case DB2:
                var db2Parser = new antlr4.DB2zSQLParser(tokens);
                db2Parser.removeErrorListeners();
                db2Parser.addErrorListener(descriptiveErrorListener);
                parsed.setParser(db2Parser);
                parsed.setParserRuleContext(db2Parser.startRule());
                break;

            case COBOL:
                var cobolParser = new Cobol85Parser(tokens);
                cobolParser.removeErrorListeners();
                cobolParser.addErrorListener(descriptiveErrorListener);
                parsed.setParser(cobolParser);
                parsed.setParserRuleContext(cobolParser.startRule());
                break;

            case JAVA:
                var javaParser = new JavaParser(tokens);
                javaParser.removeErrorListeners();
                javaParser.addErrorListener(descriptiveErrorListener);
                parsed.setParser(javaParser);
                parsed.setParserRuleContext(javaParser.compilationUnit());
                break;

            case NATURAL:
                var naturalParser = new AdabasNaturalParser(tokens);
                naturalParser.removeErrorListeners();
                naturalParser.addErrorListener(descriptiveErrorListener);
                parsed.setParser(naturalParser);
                parsed.setParserRuleContext(naturalParser.program());
                break;

            case NATURAL_MAPS:
                var naturalMapParser = new AdabasNaturalMapParser(tokens);
                naturalMapParser.removeErrorListeners();
                naturalMapParser.addErrorListener(descriptiveErrorListener);
                parsed.setParser(naturalMapParser);
                parsed.setParserRuleContext(naturalMapParser.map());
                break;

            case PYTHON:
            case CSHARP:
                throw new UnsupportedOperationException("Not implemented yet!");

            case CUSTOM:
                var customParser = (Parser) grammar.getParserClass().getConstructor(TokenStream.class).newInstance(tokens);
                customParser.removeErrorListeners();
                customParser.addErrorListener(descriptiveErrorListener);
                parsed.setParser(customParser);

                var entryRule = Utils.nvl(grammar.getEntryRule(), customParser.getRuleNames()[0]);
                var m = customParser.getClass().getMethod(entryRule);
                parsed.setParserRuleContext((ParserRuleContext) m.invoke(customParser));
                break;
        }

        if (findRules != null) {
            for (var findRule : findRules) {
                int ruleIndex = ruleIndex(parsed.getParser(), findRule);
                var findRuleList = parsed.getVisitedRules().computeIfAbsent(findRule, _ -> new ConcurrentLinkedList<>());
                findRuleList.addAll(findAll(parsed.getParserRuleContext(), ruleIndex));
            }
        }

        if (language == EnumParserLanguage.COBOL) {
            var cobolParserRunner = new CobolParserRunnerImpl() {
                @Override
                protected void analyze(final Program program) {
                    super.analyze(program);
                }
            };

            final Program program = new ProgramImpl();
            final List<String> lines = splitLines(content);
            final String hash = Utils.hashString(content);
            final ParserVisitor baseVisitor = new CobolCompilationUnitVisitorImpl(hash, lines, tokens, program);
            baseVisitor.visit(parsed.getParserRuleContext());
            cobolParserRunner.analyze(program);

            parsed.setAsg(program.getCompilationUnits().getFirst().getProgramUnits().getFirst());
        }

        TreeNode treeNode = new TreeRewriter(parsed.getParserRuleContext()).rewrite();
        parsed.setTreeNode(treeNode);

        ObjectNode jsonTree = ParseTreeJsonConverter.toJson(parsed.getParserRuleContext(), parsed.getParser(), parsed.getTokens());
        parsed.setJsonTree(jsonTree);

        String jsonString = JsonUtils.writeAsJsonString(jsonTree, true);
        parsed.setJsonString(jsonString);

        var jsonObject = JsonUtils.readAsObject(jsonString, null);
        parsed.setJsonObject(jsonObject);

        return parsed;
    }

    private static List<String> splitLines(final String preProcessedInput) {
        final Scanner scanner = new Scanner(preProcessedInput);
        final List<String> result = new ArrayList<>();

        while (scanner.hasNextLine()) {
            result.add(scanner.nextLine());
        }

        scanner.close();
        return result;
    }

    public static String getContextRawText(ParserRuleContext context) {
        if (context == null) {
            return null;
        }

        int a = context.getStart().getStartIndex();
        int b = context.getStop().getStopIndex();
        Interval interval = new Interval(a, b);

        var originalContent = context.getStart().getInputStream().getText(interval).trim();
        return originalContent.replaceAll("(?m)[ \t*>]+CAPCOSKIP", "");
    }

    public static String getContextRawText(ParserRuleContext context, Integer min, Integer max) {
        if (context == null) {
            return null;
        }

        if (max == null) {
            max = context.getStop().getStopIndex();
        }

        Interval interval = new Interval(min, max);

        var originalContent = context.getStart().getInputStream().getText(interval).trim();
        return originalContent.replaceAll("(?m)[ \t*>]+CAPCOSKIP", "");
    }

    public static String getLongestRawCode(ParserRuleContext context, Collection<? extends ParserRuleContext> listOfCtxs) {
        if (context == null) {
            return null;
        }

        Interval interval = getLongestInterval(listOfCtxs);

        var originalContent = context.getStart().getInputStream().getText(interval).trim();
        return originalContent.replaceAll("(?m)[ \t*>]+CAPCOSKIP", "");
    }

    public static Interval getLongestInterval(Collection<? extends ParserRuleContext> indices) {
        int min = Integer.MAX_VALUE;
        int max = -1;

        for (var ctx : indices) {
            int ctxStart = ctx.getStart().getStartIndex();
            int ctxStop = ctx.getStop().getStopIndex();

            if (ctxStart < min) {
                min = ctxStart;
            }

            if (ctxStop > max) {
                max = ctxStop;
            }
        }

        return new Interval(min, max);
    }

    public static List<ParserRuleContext> findAll(ParseTree root, int ruleIndex) {
        List<ParserRuleContext> out = new ArrayList<>();
        ParseTreeWalker.DEFAULT.walk(new ParseTreeListener() {
            @Override
            public void enterEveryRule(ParserRuleContext ctx) {
                if (ctx.getRuleIndex() == ruleIndex) out.add(ctx);
            }

            @Override
            public void exitEveryRule(ParserRuleContext ctx) { /* no-op */ }

            @Override
            public void visitTerminal(TerminalNode node) { /* no-op */ }

            @Override
            public void visitErrorNode(ErrorNode node) { /* no-op */ }
        }, root);
        return out;
    }

    public static <T extends ParserRuleContext> List<T> findAll(ParseTree root, Class<T> type) {
        List<T> out = new ArrayList<>();
        ParseTreeWalker.DEFAULT.walk(new ParseTreeListener() {
            @Override
            public void enterEveryRule(ParserRuleContext ctx) {
                if (type.isInstance(ctx)) out.add(type.cast(ctx));
            }

            @Override
            public void exitEveryRule(ParserRuleContext ctx) { /* no-op */ }

            @Override
            public void visitTerminal(TerminalNode node) { /* no-op */ }

            @Override
            public void visitErrorNode(ErrorNode node) { /* no-op */ }
        }, root);
        return out;
    }

    public static int ruleIndex(Parser parser, String ruleName) {
        String[] names = parser.getRuleNames();
        for (int i = 0; i < names.length; i++) {
            if (ruleName.equals(names[i])) return i;
        }
        return -1;
    }

    public static ParserRuleContext matchFirstParent(Parser parser, ParserRuleContext start, String ruleName) {
        var ruleIndex = ruleIndex(parser, ruleName);
        if (ruleIndex == -1) {
            return null;
        }

        ParserRuleContext ctx = start.getParent();

        while (ctx != null) {
            if (ctx.getRuleIndex() == ruleIndex) {
                return ctx;
            }

            ctx = ctx.getParent();
        }

        return null;
    }
}
