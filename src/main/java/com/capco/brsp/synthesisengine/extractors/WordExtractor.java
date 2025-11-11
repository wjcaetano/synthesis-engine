package com.capco.brsp.synthesisengine.extractors;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList;
import com.capco.brsp.synthesisengine.utils.Utils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WordExtractor {
    public static Map<String, Object> extractStructuredWord(String fileBase64) throws IOException {
        byte[] fileBytes = Utils.decodeBase64(fileBase64);

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return extractStructuredWord(inputStream);
        }
    }

    public static Map<String, Object> extractStructuredWord(InputStream is) throws IOException {
        XWPFDocument doc = new XWPFDocument(is);
        Deque<SectionNode> stack = new ArrayDeque<>();

        Map<String, Object> root = new ConcurrentLinkedHashMap<>();
        stack.push(new SectionNode(0, null, root));

        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph para) {
                String text = para.getText().strip();
                if (text.isEmpty()) continue;

                int level = getHeadingLevel(para);
                if (level > 0) {
                    while (!stack.isEmpty() && stack.peek().level >= level) {
                        stack.pop();
                    }

                    Map<String, Object> newSection = new ConcurrentLinkedHashMap<>();
                    putIntoParent(stack.peek().container, text, newSection);
                    stack.push(new SectionNode(level, text, newSection));
                } else {
                    addContent(stack.peek().container, text);
                }

            } else if (element instanceof XWPFTable table) {
                List<List<String>> rows = table.getRows().stream()
                        .map(r -> r.getTableCells().stream()
                                .map(c -> c.getText().strip())
                                .collect(Collectors.toList()))
                        .collect(Collectors.toList());
                addContent(stack.peek().container, Map.of("table", rows));
            }
        }

        return root;
    }

    private static void putIntoParent(Map<String, Object> parent, String heading, Object value) {
        if (parent.containsKey(heading)) {
            throw new IllegalStateException("Duplicate heading: " + heading);
        }
        parent.put(heading, value);
    }

    private static void addContent(Map<String, Object> section, Object content) {
        String lastKey = getLastKey(section);

        if (lastKey == null || section.get(lastKey) instanceof String) {
            section.computeIfAbsent("_content", k -> new ConcurrentLinkedList<>());
            ((List<Object>) section.get("_content")).add(content);
            return;
        }

        Object target = section.get(lastKey);
        if (target instanceof List list) {
            list.add(content);
        } else if (target instanceof Map) {
            List<Object> contentList = new ConcurrentLinkedList<>();
            section.put(lastKey, contentList);
            contentList.add(content);
        } else {
            List<Object> list = new ConcurrentLinkedList<>();
            list.add(target);
            list.add(content);
            section.put(lastKey, list);
        }
    }


    private static String getLastKey(Map<String, Object> map) {
        if (map.isEmpty()) return null;
        return new ConcurrentLinkedList<>(map.keySet()).get(map.size() - 1);
    }

    private static int getHeadingLevel(XWPFParagraph para) {
        String style = para.getStyle();
        if (style != null && style.matches("Heading[1-6]")) {
            return Integer.parseInt(style.substring(7));
        }
        return 0;
    }

    private record SectionNode(int level, String heading, Map<String, Object> container) {
    }
}