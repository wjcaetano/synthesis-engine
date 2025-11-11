package com.capco.brsp.synthesisengine.extractors;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.stream.Collectors;

public class HtmlExtractor {
    private static final HtmlExtractor INSTANCE = new HtmlExtractor();

    private HtmlExtractor() {
    }

    public static HtmlExtractor getInstance() {
        return INSTANCE;
    }

    public static boolean isValid(String html) {
        try {
            Document doc = Jsoup.parse(html);

            Element body = doc.body();

            return !body.children().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static String textOnly(String html) {
        return Jsoup.parse(html).text();
    }

    public static List<String> extractVisualChunks(String html) {
        Document doc = Jsoup.parse(html);
        Element body = doc.body();

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        traverseAndChunk(body, chunks, currentChunk);

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private static void traverseAndChunk(Element element, List<String> chunks, StringBuilder currentChunk) {
        for (Element child : element.children()) {
            String tag = child.tagName();

            // Start a new chunk if it's a visually distinct element
            if (tag.matches("h[1-6]|hr|section|article")) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
                currentChunk.append(child.text()).append("\n");
            } else if (tag.equals("div")) {
                // Heuristic: if div has few children and contains text, treat it like a paragraph
                if (child.children().isEmpty()) {
                    currentChunk.append(child.text()).append("\n");
                } else {
                    // Recurse into nested structure
                    traverseAndChunk(child, chunks, currentChunk);
                }
            } else if (tag.matches("p|ul|ol|li|blockquote")) {
                currentChunk.append(child.text()).append("\n");
            } else {
                // For inline or neutral tags: recurse further
                traverseAndChunk(child, chunks, currentChunk);
            }
        }
    }

    public static Map<String, Object> extractStructuredChunks(String html) {
        Document doc = Jsoup.parse(html);
        Element body = doc.body();

        Map<String, Object> root = new ConcurrentLinkedHashMap<>();
        Deque<SectionContext> sectionStack = new ArrayDeque<>();
        sectionStack.push(new SectionContext(0, root)); // root context

        traverse(body, sectionStack);
        return root;
    }

    public static List<String> extractLinkContexts(String html) {
        List<String> contexts = new ArrayList<>();

        // Parse HTML with Jsoup
        Document doc = Jsoup.parse(html);

        // Get full text (without HTML tags) to work with word positions
        String fullText = doc.text();
        String[] words = fullText.split("\\s+");

        // Extract all <a> elements
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String linkText = link.text();

            // Find the first occurrence of link text in the word array
            int index = -1;
            for (int i = 0; i < words.length; i++) {
                if (words[i].contains(linkText)) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                int start = Math.max(0, index - 100);
                int end = Math.min(words.length, index + 101);

                StringBuilder sb = new StringBuilder();
                for (int i = start; i < end; i++) {
                    sb.append(words[i]).append(" ");
                }

                contexts.add("Link: " + link.attr("href") + "\nContext: " + sb.toString().trim());
            }
        }

        return contexts;
    }

    private static void traverse(Element element, Deque<SectionContext> sectionStack) {
        for (Element child : element.children()) {
            String tag = child.tagName();

            if (tag.matches("h[1-6]")) {
                int level = Integer.parseInt(tag.substring(1));
                String heading = child.text();

                // Pop stack to the correct level
                while (!sectionStack.isEmpty() && sectionStack.peek().level >= level) {
                    sectionStack.pop();
                }

                Map<String, Object> newSection = new ConcurrentLinkedHashMap<>();
                newSection.put("_content", new ArrayList<>());
                sectionStack.peek().map.put(heading, newSection);

                sectionStack.push(new SectionContext(level, newSection));
            } else if (tag.equals("p") || tag.equals("blockquote")) {
                appendContent(sectionStack.peek(), child.text());
            } else if (tag.equals("ul") || tag.equals("ol")) {
                List<String> list = child.children().stream()
                        .filter(e -> e.tagName().equals("li"))
                        .map(Element::text)
                        .collect(Collectors.toList());
                appendContent(sectionStack.peek(), list);
            } else if (tag.equals("table")) {
                List<List<String>> table = new ArrayList<>();
                for (Element row : child.select("tr")) {
                    List<String> rowData = row.select("th, td").stream()
                            .map(Element::text)
                            .collect(Collectors.toList());
                    table.add(rowData);
                }
                appendContent(sectionStack.peek(), table);
            } else if (tag.equals("div") && child.children().isEmpty()) {
                appendContent(sectionStack.peek(), child.text());
            } else {
                traverse(child, sectionStack); // Recurse into other containers
            }
        }
    }

    private static void appendContent(SectionContext context, Object content) {
        List<Object> contentList = (List<Object>) context.map.get("_content");
        if (contentList == null) {
            return;
        }
        contentList.add(content);
    }

    private static class SectionContext {
        int level;
        Map<String, Object> map;

        SectionContext(int level, Map<String, Object> map) {
            this.level = level;
            this.map = map;
        }
    }
}
