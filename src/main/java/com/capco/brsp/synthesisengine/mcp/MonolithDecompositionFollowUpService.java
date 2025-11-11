package com.capco.brsp.synthesisengine.mcp;

import com.capco.brsp.synthesisengine.dto.AgentDto;
import com.capco.brsp.synthesisengine.service.LLMSpringService;
import com.capco.brsp.synthesisengine.service.ScriptService;
import com.capco.brsp.synthesisengine.utils.FileUtils;
import com.capco.brsp.synthesisengine.utils.JsonUtils;
import com.capco.brsp.synthesisengine.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonolithDecompositionFollowUpService {

    private final ScriptService scriptService;
    private final LLMSpringService llmSpringService;

    public String followUp(Map<String, Object> projectContext) throws Exception {
        String uid = Utils.castOrDefault(projectContext.get("monolith.uid"), String.class, null);
        if (uid == null || uid.isBlank()) {
            String lastContent = Utils.castOrDefault(projectContext.get("content"), String.class, null);
            if (lastContent == null || lastContent.isBlank()) {
                throw new IllegalStateException("No previous LLM content found to extract UID from and monolith.uid is missing.");
            }
            Matcher uidMatcher = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matcher(lastContent);
            if (!uidMatcher.find()) {
                throw new IllegalStateException("Could not find a UID in the previous LLM response and monolith.uid is missing. Content: \n" + lastContent);
            }
            uid = uidMatcher.group(0);
            projectContext.put("monolith.uid", uid);
        }

        UUID projectUUID = (UUID) projectContext.get("projectUUID");
        if (projectUUID == null) {
            throw new IllegalStateException("projectUUID not present in projectContext");
        }
        Path baseDir = FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH;
        Path targetDir = baseDir.resolve(projectUUID + "-mcp").resolve("monolith-decomposition").resolve("reports");
        Files.createDirectories(targetDir);

        Path reportJsonFile = targetDir.resolve("monolith_decomposition_report.json");

        int attempts = projectContext.get("monolith.maxAttempts") instanceof Number ? ((Number) projectContext.get("monolith.maxAttempts")).intValue() : 600;
        int baseWaitMillis = projectContext.get("monolith.waitMillis") instanceof Number ? ((Number) projectContext.get("monolith.waitMillis")).intValue() : 5000;
        int maxWaitMillis = 15000;
        int stagnantThreshold = 20;

        Integer lastProgress = null;
        int stagnantCount = 0;

        for (int i = 0; i < attempts; i++) {
            String progressPrompt = """
You are tracking an ongoing monolith decomposition job.
Use ONLY the MCP monolith tools to check the current numeric progress (0..100) for UID: %s.
Return ONLY the number (no text, no percent sign, no code fences).
If it is completed, return 100.
""".formatted(uid);

            String progressResult = scriptService.handleAgent(projectContext, progressPrompt, null);
            Integer progress = extractProgress(progressResult);
            if (progress != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> monolith = projectContext.get("monolith") instanceof Map ? (Map<String, Object>) projectContext.get("monolith") : new java.util.LinkedHashMap<>();
                monolith.put("uid", uid);
                monolith.put("progress", progress);
                projectContext.put("monolith", monolith);

                if (lastProgress != null && progress.equals(lastProgress)) {
                    stagnantCount++;
                } else {
                    stagnantCount = 0;
                }
                lastProgress = progress;

                if (progress >= 100 || stagnantCount >= stagnantThreshold) {
                    int reportMaxTokens = projectContext.get("monolith.reportMaxTokens") instanceof Number
                            ? ((Number) projectContext.get("monolith.reportMaxTokens")).intValue() : 8000;

                    Map<String, Object> agentState = pushLowTempAgent(projectContext, 0.2, reportMaxTokens);
                    try{
                        if (Files.exists(reportJsonFile)) {
                            String existingJson = Files.readString(reportJsonFile);
                            persistJsonOutputs(projectContext, uid, existingJson, reportJsonFile);
                            return "Monolith decomposition finished and results persisted in: " + targetDir.toAbsolutePath();
                        }

                        String fetchPrompt = """
Use ONLY the MCP tools to fetch the FINAL JSON report for UID: %s.
Return a SINGLE HTTP/HTTPS URL to download the JSON.
Do not return JSON content. No explanations.
""".formatted(uid);

                        String fetchResultUrlOnly = scriptService.handleAgent(projectContext, fetchPrompt, null, 5);
                        String jsonContent = null;
                        String directUrl = extractUrl(fetchResultUrlOnly);

                        if(directUrl != null){
                            try {
                                URI uri = URI.create(directUrl);
                                String scheme = uri.getScheme();
                                boolean unsupportedScheme = (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"));

                                if (unsupportedScheme) {
                                    log.info("Unsupported URL scheme for '{}'. Falling back to MCP JSON fetch.", directUrl);
                                    jsonContent = fetchJsonFromMcp(projectContext, uid);
                                } else {
                                    HttpClient client = HttpClient.newBuilder()
                                            .connectTimeout(Duration.ofSeconds(15))
                                            .build();
                                    HttpRequest request = HttpRequest.newBuilder()
                                            .uri(uri)
                                            .timeout(Duration.ofSeconds(30))
                                            .GET()
                                            .build();
                                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                        jsonContent = response.body();
                                    } else {
                                        log.warn("Failed to download JSON from URL (status {}): {}. Falling back to MCP JSON fetch.", response.statusCode(), directUrl);
                                        jsonContent = fetchJsonFromMcp(projectContext, uid);
                                    }
                                }
                            } catch (Exception ex) {
                                log.warn("Error downloading JSON from URL '{}': {}. Falling back to MCP JSON fetch.", directUrl, ex.getMessage());
                                try {
                                    jsonContent = fetchJsonFromMcp(projectContext, uid);
                                } catch (Exception inner) {
                                    log.warn("MCP JSON fetch also failed: {}", inner.getMessage());
                                }
                            }
                        } else {
                            try {
                                jsonContent = fetchJsonFromMcp(projectContext, uid);
                            } catch (Exception ex) {
                                log.warn("MCP JSON fetch failed: {}", ex.getMessage());
                            }
                        }

                        if (jsonContent != null && !jsonContent.isBlank()) {
                            persistJsonOutputs(projectContext, uid, jsonContent, reportJsonFile);
                            return "Monolith decomposition finished and results persisted in: " + targetDir.toAbsolutePath();
                        }
                    } finally {
                        popAgent(projectContext, agentState);
                    }
                }
            }

            int factor = i + (i / 50);
            long computed = (long) baseWaitMillis * (long) Math.max(factor, 1);
            int waitMillis = (int) Math.min(computed, (long) maxWaitMillis);
            waitMillis = Math.max(waitMillis, 250);
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        return "Monolith decomposition still in progress. Last known progress: " + (lastProgress != null ? lastProgress + "%" : "unknown") + ".";
    }

    private static Integer extractProgress(String text) {
        if (text == null) return null;
        String t = text.trim();

        Matcher onlyNumber = Pattern.compile("^(?:\\D*?)(\\d{1,3})(?:\\D*?)$").matcher(t);
        if (onlyNumber.find()) {
            try {
                int v = Integer.parseInt(onlyNumber.group(1));
                if (v >= 0 && v <= 100) return v;
            } catch (Exception ignored) {}
        }

        Matcher progressName = Pattern.compile("(?i)progress\\D{0,10}(\\d{1,3})\\s*%?").matcher(text);
        if (progressName.find()) {
            try {
                int v = Integer.parseInt(progressName.group(1));
                if (v >= 0 && v <= 100) return v;
            } catch (Exception ignored) {}
        }

        Matcher withPercent = Pattern.compile("(\\d{1,3})\\s*%").matcher(text);
        if (withPercent.find()) {
            try {
                int v = Integer.parseInt(withPercent.group(1));
                if (v >= 0 && v <= 100) return v;
            } catch (Exception ignored) {}
        }

        Matcher anyNumber = Pattern.compile("(\\d{1,3})").matcher(text);
        Integer best = null;
        while (anyNumber.find()) {
            try {
                int v = Integer.parseInt(anyNumber.group(1));
                if (v >= 0 && v <= 100) best = (best == null) ? v : Math.max(best, v);
            } catch (Exception ignored) {}
        }
        return best;
    }

    private static String extractJsonBlock(String text) {
        if (text == null) return null;
        Matcher jsonMatcher = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE).matcher(text);
        return jsonMatcher.find() ? jsonMatcher.group(1).trim() : null;
    }

    private static String extractAnyJson(String text) {
        if (text == null) return null;
        String t = text.trim();
        String fenced = extractJsonBlock(t);
        if (fenced != null && !fenced.isBlank()) return fenced;
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
            return t;
        }
        int objStart = t.indexOf('{');
        int arrStart = t.indexOf('[');
        int start = (objStart >= 0 && arrStart >= 0) ? Math.min(objStart, arrStart) : Math.max(objStart, arrStart);
        if (start < 0) return null;
        for (int end = t.length(); end > start + 1; end--) {
            String candidate = t.substring(start, end).trim();
            try {
                Object parsed = JsonUtils.readAsObject(candidate, Object.class);
                if (parsed != null) return candidate;
            } catch (Exception ignore) {}
        }
        return null;
    }

    private static String extractUrl(String text) {
        if (text == null) return null;
        Matcher anyUrl = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE).matcher(text);
        return anyUrl.find() ? anyUrl.group(0).trim() : null;
    }

    private String fetchJsonFromMcp(Map<String, Object> projectContext, String uid) throws Exception {
        String strictJsonPrompt = ("Use ONLY the MCP tools to fetch the FINAL JSON report for UID: %s.\n" +
                "Return STRICT JSON only (raw or fenced ```json ... ```). No URLs. No explanations.").formatted(uid);

        String response = scriptService.handleAgent(projectContext, strictJsonPrompt, null, 2);
        String json = extractAnyJson(response);
        if (json != null && !json.isBlank()) {
            return json;
        }

        Thread.sleep(600L);

        String secondTry = ("Return ONLY the JSON content for UID: %s using MCP tools. No URLs. No text.").formatted(uid);
        response = scriptService.handleAgent(projectContext, secondTry, null, 1);
        json = extractAnyJson(response);
        return json;
    }

    private static void persistJsonOutputs(Map<String, Object> projectContext,
                                           String uid,
                                           String jsonContent,
                                           Path reportJsonFile) throws Exception {
        Object reportJson;
        try {
            reportJson = JsonUtils.readAsObject(jsonContent, Object.class);
        } catch (Throwable t) {
            reportJson = jsonContent;
        }

        String jsonOut = (reportJson instanceof String)
                ? (String) reportJson
                : JsonUtils.writeAsJsonString(reportJson, true);

        Files.writeString(reportJsonFile, jsonOut, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        @SuppressWarnings("unchecked")
        Map<String, Object> monolith = projectContext.get("monolith") instanceof Map ? (Map<String, Object>) projectContext.get("monolith") : new java.util.LinkedHashMap<>();
        monolith.put("uid", uid);
        monolith.put("reportJson", reportJson);
        monolith.put("reportJsonPath", "monolith-decomposition/reports/monolith_decomposition_report.json");
        projectContext.put("monolith", monolith);

        @SuppressWarnings("unchecked")
        Map<String, Object> api = projectContext.get("$api") instanceof Map ? (Map<String, Object>) projectContext.get("$api") : new java.util.LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> files = api.get("files") instanceof Map ? (Map<String, Object>) api.get("files") : new java.util.LinkedHashMap<>();
        files.put("monolith_decomposition_report.json", Utils.encodeBase64(jsonOut));
        api.put("files", files);
        projectContext.put("$api", api);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pushLowTempAgent(Map<String, Object> projectContext, Double temperature, Integer maxTokens) {
        Object originalAgent = projectContext.get("agent");
        var agentDto = llmSpringService.parseAgentConfig(originalAgent);

        AgentDto lowTemp = new AgentDto();
        lowTemp.setName(agentDto.getName());
        lowTemp.setProvider(agentDto.getProvider());
        lowTemp.setModel(agentDto.getModel());
        lowTemp.setEmbeddingModel(agentDto.getEmbeddingModel());
        lowTemp.setIsEmbedding(agentDto.getIsEmbedding());
        lowTemp.setDeploymentName(agentDto.getDeploymentName());
        lowTemp.setTemperature(temperature != null ? temperature : 0.2);
        lowTemp.setMaxTokens(maxTokens != null ? maxTokens : 8000);
        lowTemp.setTopP(agentDto.getTopP());
        lowTemp.setFrequencyPenalty(agentDto.getFrequencyPenalty());
        lowTemp.setPresencePenalty(agentDto.getPresencePenalty());
        lowTemp.setTopK(agentDto.getTopK());
        lowTemp.setMaxTurns(agentDto.getMaxTurns());
        lowTemp.setRetry(agentDto.getRetry());
        lowTemp.setSystemInstructions(agentDto.getSystemInstructions());
        lowTemp.setStopSequences(agentDto.getStopSequences());
        lowTemp.setBefore(agentDto.getBefore());
        lowTemp.setAfter(agentDto.getAfter());
        lowTemp.setTools(agentDto.getTools());
        lowTemp.setMetadata(agentDto.getMetadata());

        Map<String, Object> meta = agentDto.getMetadata() instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) agentDto.getMetadata())
                : new LinkedHashMap<>();
        meta.put("inputMode", "url-only");
        meta.put("mcpTools", List.of(
                "spring_ai_mcp_client_monolith_decomposition_sse_get_report_json",
                "spring_ai_mcp_client_monolith_decomposition_sse_get_report_pdf",
                "spring_ai_mcp_client_monolith_decomposition_sse_get_progress_number"
        ));

        lowTemp.setMetadata(meta);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("originalAgent", originalAgent);
        projectContext.put("agent", lowTemp);
        return state;
    }

    private void popAgent(Map<String, Object> projectContext, Map<String, Object> state) {
        if (state != null && state.containsKey("originalAgent")) {
            projectContext.put("agent", state.get("originalAgent"));
        }
    }
}
