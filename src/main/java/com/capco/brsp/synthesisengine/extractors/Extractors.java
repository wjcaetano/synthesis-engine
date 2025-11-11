package com.capco.brsp.synthesisengine.extractors;

import com.capco.brsp.synthesisengine.service.IScriptService;
import com.capco.brsp.synthesisengine.utils.*;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Extractors {
    private static final Extractors INSTANCE = new Extractors();

    private Extractors() {
    }

    public static Extractors getInstance() {
        return INSTANCE;
    }

    private static final Map<String, Function<String, Object>> EXTRACTORS = new ConcurrentLinkedHashMap<>();

    static {
        EXTRACTORS.put("REGEX_HTML", (text) -> Utils.getAllRegexGroups(text, "(<html[\\s\\S]*?>[\\s\\S]+?<\\/html>)"));
        EXTRACTORS.put("REGEX_MARKDOWN_BIGGEST", (text) -> Utils.getAllRegexGroups(text, "```(?:[\\S]+\\n)?([\\s\\S]+)```"));
        EXTRACTORS.put("HTML_TEXT", HtmlExtractor::textOnly);
        EXTRACTORS.put("HTML_VISUAL_CHUNKS", HtmlExtractor::extractVisualChunks);
        EXTRACTORS.put("HTML_LINK_CONTEXT", HtmlExtractor::extractLinkContexts);
        EXTRACTORS.put("HTML_STRUCTURED_CHUNKS", (htmlText) -> MarkdownUtils.formatMarkdown(HtmlExtractor.extractStructuredChunks(htmlText)));
        EXTRACTORS.put("BASE64_EXCEL_TO_MARKDOWN", (base64Text) -> {
            try {
                var structuredExcel = ExcelExtractor.extractTextFromExcelBase64(base64Text);
                return MarkdownUtils.formatMarkdown(structuredExcel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        EXTRACTORS.put("ANY_TO_MARKDOWN", (text) -> {
            try {
                return Extractors.detectAndExtract(text);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static String detectAndExtract(String content) throws IOException {
        String contentType = "text/plain";
        String contentContent = content;
        if (content.startsWith("data:")) {
            contentType = Utils.getRegexGroup(content, "^data:(.*?);base64,([\\s\\S]+)$", 1);
            contentContent = Utils.getRegexGroup(content, "^data:(.*?);base64,([\\s\\S]+)$", 2);
        }

        if (JsonUtils.isValidJson(contentContent)) {
            contentType = "application/json";
        } else if (YamlUtils.isValidYaml(contentContent)) {
            contentType = "application/yaml";
        } else if (XmlUtils.isValidXml(contentContent)) {
            contentType = "application/xml";
        } else if (HtmlExtractor.isValid(contentContent)) {
            contentType = "text/html";
        }

        return switch (contentType) {
            case "text/plain", "text/markdown" -> contentContent;
            case "text/html" -> MarkdownUtils.formatMarkdown(HtmlExtractor.extractStructuredChunks(contentContent));
            case "text/csv" -> MarkdownUtils.formatMarkdown(CsvExtractor.parseCsv(contentContent));
            case "text/tab-separated-values" ->
                    MarkdownUtils.formatMarkdown(CsvExtractor.parseCsv(CSVFormat.TDF, contentContent));
            case "application/json" -> MarkdownUtils.formatMarkdown(JsonUtils.readAs(contentContent, Object.class));
            case "application/xml" -> MarkdownUtils.formatMarkdown(XmlUtils.readAs(contentContent, Object.class));
            case "application/yaml" -> MarkdownUtils.formatMarkdown(YamlUtils.readYAML(contentContent));
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel" ->
                    MarkdownUtils.formatMarkdown(ExcelExtractor.extractTextFromExcelBase64(contentContent));
            case "application/vnd.oasis.opendocument.spreadsheet" ->
                    MarkdownUtils.formatMarkdown(ExcelExtractor.extractFromODSBase64(contentContent));
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword",
                 "application/vnd.oasis.opendocument.text" ->
                    MarkdownUtils.formatMarkdown(WordExtractor.extractStructuredWord(contentContent));
            case null, default ->
                    throw new IllegalStateException("Failed to automatically convert the content to text.");
        };
    }

    public static String extract(IScriptService scriptService, List<Object> parsedTransformParams, String content, Map<String, Object> context) {
        String extractPatternOrKey = Utils.getParam(parsedTransformParams, 0, null);
        Function<String, Object> extractor = Utils.nvl(EXTRACTORS.get(extractPatternOrKey.toUpperCase()), (text) -> Utils.getAllRegexGroups(text, extractPatternOrKey));

        var obj = extractor.apply(content);
        List<Object> selectors = new LinkedList<>();
        if (parsedTransformParams.size() < 2) {
            if (obj instanceof Collection objCollection) {
                assert objCollection.size() == 1;
                selectors.add("$.[0]['1']");
            }
        } else {
            selectors = parsedTransformParams.subList(1, parsedTransformParams.size());
        }

        Object extractResult = applyExtractions(scriptService, obj, context, selectors);

        if (extractResult instanceof String extractResultString) {
            content = extractResultString;
        } else {
            throw new RuntimeException("The @@@extract result should be a String!");
        }

        return content;
    }

    public static Object applyExtractions(IScriptService scriptService, Object input, Map<String, Object> projectContext, List<Object> extractParams) {
        Object result = input;

        for (int i = 0; i < extractParams.size(); i++) {
            String extractExpression = Utils.getParam(extractParams, i, null);

            if (extractExpression.trim().startsWith("@@@")) {
                projectContext.put("result", result);
                try {
                    result = scriptService.autoEval(extractExpression);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                projectContext.remove("result");
            } else if (scriptService.isValidSpEL(extractExpression)) {
                projectContext.put("result", result);
                result = scriptService.evalSpEL(extractExpression);
                projectContext.remove("result");
            } else if (extractExpression.startsWith("$")) {
                result = JsonPath.read(result, extractExpression);
            } else {
                String resultString;
                if (result instanceof String str) {
                    resultString = str;
                } else {
                    resultString = JsonUtils.writeAsJsonString(result, true);
                }

                result = Utils.getAllRegexMatches(resultString, extractExpression);
            }
        }

        return result;
    }
}
