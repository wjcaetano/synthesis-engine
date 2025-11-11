package com.capco.brsp.synthesisengine.configuration;

import com.capco.brsp.synthesisengine.utils.*;
import com.jayway.jsonpath.JsonPath;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Data
@Builder
public class PaginationConfig {
    private PaginationType type;
    private String nextTokenField;
    private String isLastField;
    private String itemsPath;
    private int pageSize;
    private int maxPages;
    private int maxItems;
    private int rateLimitDelayMs;
    private String startAtField;
    private String totalField;
    private String pageNumberField;

    public static PaginationConfig none() {
        return PaginationConfig.builder()
                .type(PaginationType.NONE)
                .build();
    }

    public static PaginationConfig fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return none();
        }

        String typeStr = (String) map.getOrDefault("type", "none");
        PaginationType type = PaginationType.valueOf(typeStr.toUpperCase());

        return PaginationConfig.builder()
                .type(type)
                .nextTokenField((String) map.getOrDefault("nextTokenField", "nextPageToken"))
                .isLastField((String) map.getOrDefault("isLastField", "isLast"))
                .itemsPath((String) map.getOrDefault("itemsPath", "$.issues"))
                .pageSize((int) map.getOrDefault("pageSize", 100))
                .maxPages((int) map.getOrDefault("maxPages", 100))
                .maxItems((int) map.getOrDefault("maxItems", 0))
                .rateLimitDelayMs((int) map.getOrDefault("rateLimitDelayMs", 200))
                .startAtField((String) map.getOrDefault("startAtField", "startAt"))
                .totalField((String) map.getOrDefault("totalField", "total"))
                .pageNumberField((String) map.getOrDefault("pageNumberField", "page"))
                .build();
    }

    public boolean isNone() {
        return this.type == PaginationType.NONE;
    }

    public Object getInitialState() {
        return switch (type) {
            case OFFSET -> 0;
            case PAGE_NUMBER -> 1;
            default -> null;
        };
    }

    public PaginationRequest buildRequest(Object state, Object baseBody) {
        Map<String, Object> body = baseBody instanceof Map ? new HashMap<>((Map) baseBody) : new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();

        switch (type) {
            case CURSOR:
                if (state != null) {
                    body.put(nextTokenField, state);
                }
                break;
            case OFFSET:
                int startAt = (int) state;
                body.put(startAtField, startAt);
                body.put("maxResults", pageSize);
                break;

            case PAGE_NUMBER:
                int pageNumber = (int) state;
                queryParams.put(pageNumberField, String.valueOf(state));
                queryParams.put("pageSize", String.valueOf(pageSize));
                break;
        }

        return new PaginationRequest(body, queryParams);
    }

    public List<Map<String, Object>> extractItems(Map<String, Object> response) {
        try {
            Object items = JsonPath.read(JsonUtils.writeAsJsonString(response, false), itemsPath);
            if (items instanceof List) {
                return (List<Map<String, Object>>) items;
            }
        } catch (Exception e) {
            log.warn("Failed to extract items using path {}: {}", itemsPath, e.getMessage());
        }
        return List.of();
    }

    public boolean isLastPage(Map<String, Object> response, Object currentState) {
        return switch (type) {
            case CURSOR -> {
                Boolean isLast = (Boolean) response.get(isLastField);
                yield isLast != null && isLast;
            }
            case OFFSET -> {
                Integer total = (Integer) response.get(totalField);
                int currentStartAt = (int) currentState;
                yield total != null && (currentStartAt + pageSize) >= total;
            }
            case PAGE_NUMBER -> {
                Integer totalPages = (Integer) response.get(totalField);
                int currentPageNumber = (int) currentState;
                yield totalPages != null && currentPageNumber >= totalPages;
            }
            default -> true;
        };
    }

    public Object updateState(Map<String, Object> response, Object currentState) {
        return switch (type) {
            case CURSOR -> response.get(nextTokenField);
            case OFFSET -> (int) currentState + pageSize;
            case PAGE_NUMBER -> (int) currentState + 1;
            default -> null;
        };
    }

    public enum PaginationType {
        NONE, CURSOR, OFFSET, PAGE_NUMBER
    }

    @Data
    @AllArgsConstructor
    public static class PaginationRequest {
        private Object body;
        private Map<String, String> queryParams;

        public boolean hasQueryParams() {
            return queryParams != null && !queryParams.isEmpty();
        }

        public String getQueryString() {
            if (!hasQueryParams()) {
                return "";
            }
            return queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
        }
    }
}
