package com.capco.brsp.synthesisengine.utils;

import java.util.List;
import java.util.Map;

public class MarkdownUtils {
    private static final MarkdownUtils INSTANCE = new MarkdownUtils();

    private MarkdownUtils() {
    }

    public static MarkdownUtils getInstance() {
        return INSTANCE;
    }

    public static String formatMarkdown(Object content) {
        return formatMarkdown(content, 1);
    }

    @SuppressWarnings("unchecked")
    public static String formatMarkdown(Object content, int level) {
        if (content instanceof String) {
            return (String) content;
        }

        if (content instanceof Map<?, ?> contentMap) {
            StringBuilder sb = new StringBuilder();
            Map<Object, Object> map = (Map<Object, Object>) content;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                sb.append("\n").append("#".repeat(level)).append(" ").append(entry.getKey()).append("\n\n");
                sb.append(formatMarkdown(entry.getValue(), level + 1)).append("\n");
            }
            return sb.toString().trim();
        }

        if (content instanceof List<?> list) {
            if (isListOfLists(list)) {
                return formatMarkdownTable((List<List<Object>>) list);
            }

            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map) {
                    sb.append(formatMarkdown(item, level)).append("\n");
                } else if (item instanceof List) {
                    sb.append(formatMarkdown(item, level)).append("\n");
                } else {
                    sb.append("- ").append(item.toString()).append("\n");
                }
            }
            return sb.toString().trim();
        }

        return content != null ? content.toString() : "";
    }

    private static boolean isListOfLists(List<?> list) {
        if (list.isEmpty()) return false;
        for (Object item : list) {
            if (!(item instanceof List)) {
                return false;
            }
        }
        return true;
    }

    public static String formatMarkdownTable(List<List<Object>> table) {
        return formatMarkdownTable(table, 30);
    }

    public static String formatMarkdownTable(List<List<Object>> table, int maxColWidth) {
        if (table.isEmpty()) return "";

        int colCount = table.getFirst().size();

        // Wrap and normalize cell content
        List<List<List<String>>> wrappedTable = new ConcurrentLinkedList<>();
        List<Integer> rowHeights = new ConcurrentLinkedList<>();

        for (List<Object> row : table) {
            List<List<String>> wrappedRow = new ConcurrentLinkedList<>();
            int maxHeight = 1;

            for (Object cell : row) {
                List<String> wrapped = wrap(cell instanceof String ? (String) cell : String.valueOf(cell), maxColWidth);
                wrappedRow.add(wrapped);
                maxHeight = Math.max(maxHeight, wrapped.size());
            }

            // Pad each cell to the same row height
            for (List<String> cellLines : wrappedRow) {
                while (cellLines.size() < maxHeight) {
                    cellLines.add("");
                }
            }

            wrappedTable.add(wrappedRow);
            rowHeights.add(maxHeight);
        }

        // Determine max width per column
        int[] colWidths = new int[colCount];
        for (List<List<String>> row : wrappedTable) {
            for (int c = 0; c < colCount; c++) {
                if (row.size() > c) {
                    for (String line : row.get(c)) {
                        colWidths[c] = Math.max(Math.max(colWidths[c], line.length()), 1);
                    }
                } else {
                    colWidths[c] = 1;
                }
            }
        }

        // Build Markdown table string
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < wrappedTable.size(); r++) {
            List<List<String>> row = wrappedTable.get(r);
            int rowHeight = rowHeights.get(r);
            for (int lineIdx = 0; lineIdx < rowHeight; lineIdx++) {
                sb.append("| ");
                for (int c = 0; c < colCount; c++) {
                    if (row.size() > c) {
                        sb.append(padRight(row.get(c).get(lineIdx), colWidths[c])).append(" | ");
                    } else {
                        sb.append(padRight("", colWidths[c])).append(" | ");
                    }
                }
                sb.append("\n");
            }

            // Add header separator after the first row
            if (r == 0) {
                sb.append("|");
                for (int w : colWidths) {
                    sb.append(" ").append("-".repeat(w)).append(" |");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ConcurrentLinkedList<>();
        while (text.length() > width) {
            int breakPoint = text.lastIndexOf(' ', width);
            if (breakPoint == -1) breakPoint = width;
            lines.add(text.substring(0, breakPoint));
            text = text.substring(breakPoint).stripLeading();
        }
        if (!text.isEmpty()) lines.add(text);
        return lines;
    }

    private static String padRight(String text, int width) {
        return String.format("%-" + width + "s", text);
    }
}
