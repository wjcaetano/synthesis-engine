package com.capco.brsp.synthesisengine.tools;

import com.bazaarvoice.jolt.Chainr;
import com.capco.brsp.synthesisengine.dto.ToolDto;
import com.capco.brsp.synthesisengine.service.ContextService;
import com.capco.brsp.synthesisengine.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.util.FileUtil;
import org.jline.nativ.OSInfo;
import org.jline.utils.InputStreamReader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.capco.brsp.synthesisengine.utils.FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH;

@Slf4j
@Component
public class ToolsFunction {
    private final ContextService contextService;

    private final RestTemplate restTemplate = new RestTemplate();

    public ToolsFunction(ContextService contextService) {
        this.contextService = contextService;
    }

    @ToolName(name = "arxiv")
    public Object arxiv(String query, int maxResults) throws Exception {
        String cleanedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://arxiv.org/search/?query=" + cleanedQuery + "&searchtype=all&source=header";

        return WebSearchUtils.parseFromUrl(url, maxResults);
    }

    @ToolName(name = "bing")
    public Object bing(String query, int maxResults) throws Exception {
        String cleanedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.bing.com/search?q=" + cleanedQuery + "&cc=US&setlang=en-US&count=50";

        return WebSearchUtils.parseFromUrl(url, maxResults);
    }

    @ToolName(name = "duckduckgo")
    public Object duckDuckGo(String query, int maxResults) throws Exception {
        String cleanedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://duckduckgo.com/?t=h_&q=" + cleanedQuery;

        return WebSearchUtils.parseFromUrl(url, maxResults);
    }

    @ToolName(name = "api_call")
    public Object apiCall(@ToolParameter(name = "url") String url, @ToolParameter(name = "method", required = false) String method, @ToolParameter(name = "body", required = false) Object body, @ToolParameter(name = "headers", required = false) Map<String, String> headers) {
        if (method == null) {
            method = "GET";
        }
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

        if (headers == null) {
            headers = new ConcurrentLinkedHashMap<>();
        }
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::add);

        return restTemplate.execute(url, httpMethod, request -> {
            request.getHeaders().putAll(httpHeaders);
            request.getHeaders().add(HttpHeaders.CONNECTION, "close");

            if (body != null) {
                new MappingJackson2HttpMessageConverter().write(body, MediaType.APPLICATION_JSON, request);
            }
        }, response -> {
            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (responseBody.isEmpty()) return null;

            if ((responseBody.startsWith("{") && responseBody.endsWith("}")) ||
                    (responseBody.startsWith("[") && responseBody.endsWith("]"))) {

                return new ObjectMapper().readValue(responseBody, Object.class);
            }

            return responseBody;
        });
    }

    @ToolName(name = "jolt")
    public Object jolt(@ToolParameter(name = "input") Object input, @NonNull @ToolParameter(name = "spec") String spec) throws JsonProcessingException {
        Object inputObj = input instanceof String inputString ? JsonUtils.readAsObject(inputString, new ConcurrentLinkedHashMap<>()) : input;
        Object specMap = JsonUtils.readAsObject(spec, new ConcurrentLinkedList<>());

        Chainr chainr = Chainr.fromSpec(specMap);
        return chainr.transform(inputObj);
    }

    @ToolName(name = "confluence")
    public String confluence(@ToolParameter(name = "documents") Map<String, String> documents, @NonNull @ToolParameter(name = "user") String user, @NonNull @ToolParameter(name = "token") String token, @NonNull @ToolParameter(name = "url") String url, @NonNull @ToolParameter(name = "folderID") String folderID, @NonNull @ToolParameter(name = "spaceKey") String spaceKey) {
        var confluenceAuth = Utils.createBasicAuthHeader(user, token);
        var confluenceAPIHeaders = Map.of("Content-Type", "application/json", "Authorization", confluenceAuth);

        log.info("[CONFLUENCE] Checking if there exist any children pages inside the '{}' page ID of '{}' space", folderID, spaceKey);
        var childrenResponse = apiCall(url + "/" + folderID + "/child/page?limit=1000", "GET", null, confluenceAPIHeaders);
        if (childrenResponse instanceof Map<?, ?> childrenMap) {
            var children = (List<Map<String, Object>>) childrenMap.get("results");
            log.info("[CONFLUENCE] Found {} children pages that will be deleted before sending the new pages...", children.size());
            children.forEach(child -> {
                var childId = child.get("id");
                apiCall(url + "/" + childId, "DELETE", null, confluenceAPIHeaders);
            });
        }

        log.info("[CONFLUENCE] Now creating {} pages...", documents.size());
        documents.forEach((key, confluencePageContent) -> {
            var confluencePageName = FileUtils.removeFileExtension(key);

            var body = new ConcurrentLinkedHashMap<>();
            body.put("type", "page");
            body.put("title", confluencePageName);
            body.put("space", new ConcurrentLinkedHashMap<>(Map.of("key", spaceKey)));
            body.put("ancestors", List.of(new ConcurrentLinkedHashMap<>(Map.of("id", folderID))));
            body.put("body", Map.of("storage", Map.of("value", confluencePageContent, "representation", "storage")));

            apiCall(url, "POST", body, confluenceAPIHeaders);
        });

        var returnMessage = String.format("[CONFLUENCE] Successfully created %d pages!", documents.size());
        log.info(returnMessage);

        return returnMessage;
    }

    @ToolName(name = "shell_run")
    public String shellRun(@ToolParameter(name = "os") String os, @ToolParameter(name = "command") String command) throws IOException, InterruptedException {
        var flowKey = contextService.getFlowKey();
        if (flowKey == null) {
            throw new IllegalStateException("Couldn't get the flowKey for executing the 'shell_run' tool");
        }

        String folderPath = FileUtils.absolutePathJoin(USER_TEMP_PROJECTS_FOLDER_PATH, flowKey).toString();

        String shellTool = "bash";
        String shellCommandArg = "-c";
        if (Objects.equals(OSInfo.getOSName(), "Windows")) {
            shellTool = "cmd";
            shellCommandArg = "/c";
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(shellTool, shellCommandArg, command);

        processBuilder.directory(new File(folderPath));

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> lines = new ConcurrentLinkedList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Command failed with exit code: {}", exitCode);
        }

        return String.join("\n", lines);
    }

    @ToolName(name = "glob")
    public String globTool(@ToolParameter(name = "pattern") String pattern) throws IOException, InterruptedException {
        boolean isWindows = OSInfo.getOSName().toLowerCase().contains("windows");

        String command;
        if (isWindows) {
            // PowerShell: glob and sort by LastWriteTime descending
            command = String.format(
                    "powershell -Command \"Get-ChildItem -Recurse -File -Path . -Include %s | " +
                            "Sort-Object LastWriteTime -Descending | ForEach-Object { $_.FullName }\"",
                    pattern
            );
            return shellRun("windows", command);
        } else {
            command = String.format("find . -type f -path \"%s\" -printf \"%%T@ %%p\\n\" | sort -nr | cut -d' ' -f2-", pattern);
            return shellRun("unix", command);
        }
    }

    @ToolName(name = "grep")
    public String grepTool(@ToolParameter(name = "regex") String regex,
                           @ToolParameter(name = "include") String includePattern) throws IOException, InterruptedException {
        boolean isWindows = OSInfo.getOSName().toLowerCase().contains("windows");

        String command;
        if (isWindows) {
            command = String.format(
                    "powershell -Command \"Get-ChildItem -Recurse -Include %s | " +
                            "Select-String -Pattern '%s' | ForEach-Object { \\\"$($_.Path):$($_.LineNumber): $($_.Line)\\\" }\"",
                    includePattern, regex.replace("\"", "\\\"")
            );
            return shellRun("windows", command);
        } else {
            command = String.format("find . -type f -name \"%s\" -exec grep -EnIH \"%s\" {} +", includePattern, regex);
            return shellRun("unix", command);
        }
    }

    @ToolName(name = "ls")
    public String lsTool(@ToolParameter(name = "path") String absolutePath,
                         @ToolParameter(name = "ignore") List<String> ignoreGlobs) throws IOException, InterruptedException {
        boolean isWindows = OSInfo.getOSName().toLowerCase().contains("windows");

        String command;
        if (isWindows) {
            String ignoreClause = (ignoreGlobs != null && !ignoreGlobs.isEmpty())
                    ? ignoreGlobs.stream().map(g -> "-not ( $_.FullName -like '" + g + "' )")
                    .collect(Collectors.joining(" -and "))
                    : "";

            command = String.format(
                    "powershell -Command \"Get-ChildItem -Recurse -Path '%s' | Where-Object { %s } | ForEach-Object { $_.FullName }\"",
                    absolutePath,
                    ignoreClause.isEmpty() ? "$true" : ignoreClause
            );
            return shellRun("windows", command);
        } else {
            String ignoreClause = ignoreGlobs != null && !ignoreGlobs.isEmpty()
                    ? ignoreGlobs.stream().map(g -> "! -path \"" + g + "\"").collect(Collectors.joining(" "))
                    : "";

            command = String.format("find \"%s\" %s", absolutePath, ignoreClause);
            return shellRun("unix", command);
        }
    }

    @ToolName(name = "view")
    public String viewFile(@ToolParameter(name = "file_path") String absoluteFilePath,
                           @ToolParameter(name = "max_lines") Integer maxLines) throws IOException, InterruptedException {
        boolean isWindows = OSInfo.getOSName().toLowerCase().contains("windows");
        int lines = (maxLines != null) ? maxLines : 2000;

        String command;
        if (isWindows) {
            command = String.format(
                    "powershell -Command \"Get-Content -Path '%s' -TotalCount %d\"",
                    absoluteFilePath, lines
            );
            return shellRun("windows", command);
        } else {
            command = String.format("head -n %d \"%s\"", lines, absoluteFilePath);
            return shellRun("unix", command);
        }
    }

    @ToolName(name = "replace_in_file")
    public String replaceInFile(
            @ToolParameter(name = "file_path") String absoluteFilePath,
            @ToolParameter(name = "original_value") String originalValue,
            @ToolParameter(name = "new_value") String newValue
    ) throws IOException, InterruptedException {
        var filePath = Paths.get(absoluteFilePath);
        var file = new File(absoluteFilePath);

        var fileContent = FileUtil.readAsString(file);

        if (fileContent.contains(originalValue)) {
            fileContent = fileContent.replace(originalValue, newValue);
            FileUtils.writeFile(filePath, fileContent, false);
            return "Replaced all occurrences of '" + originalValue + "' with '" + newValue + "' in file: " + absoluteFilePath + "\n The new content looks like below:\n\n[CONTENT]\n" + fileContent;
        } else {
            return "Maybe the original_value has something wrong because as no occurrences of '" + originalValue + "' found in file: " + absoluteFilePath;
        }
    }

    @ToolName(name = "new_file")
    public String replaceInFile(
            @ToolParameter(name = "file_path") String absoluteFilePath,
            @ToolParameter(name = "value") String value
    ) {
        var filePath = Paths.get(absoluteFilePath);

        FileUtils.writeFile(filePath, value, false);
        return "Value was written to the file: " + absoluteFilePath;
    }

    public Object invokeToolMethod(ToolDto dto) {
        var matchedMethod = getToolMethod(dto.getName());

        var parameters = matchedMethod.getParameters();
        var paramAnnotations = matchedMethod.getParameterAnnotations();

        List<Object> args = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            String paramName = null;
            for (var annotation : paramAnnotations[i]) {
                if (annotation instanceof ToolParameter) {
                    paramName = ((ToolParameter) annotation).name();
                    break;
                }
            }
            if (paramName == null) {
                throw new RuntimeException("Missing @ToolParameter annotation on method parameter");
            }

            Object value = dto.getParameters().get(paramName);

            Class<?> expectedType = parameters[i].getType();
            value = castToExpectedType(value, expectedType);

            args.add(value);
        }

        try {
            return matchedMethod.invoke(this, args.toArray());
        } catch (Exception e) {
            throw new RuntimeException("Method invocation failed", e);
        }
    }

    private Object castToExpectedType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value.toString());
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());
        return value;
    }

    public Method getToolMethod(String name) {
        return Arrays.stream(ToolsFunction.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(ToolName.class) && m.getAnnotation(ToolName.class).name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public String getToolDescription(String name) {
        var method = getToolMethod(name);

        return getToolDescription(method);
    }

    public String getToolDescription(Method method) {
        var name = (method.isAnnotationPresent(ToolName.class)) ? method.getAnnotation(ToolName.class).name() : null;

        var parameters = Arrays.stream(method.getParameters()).filter(it -> it.isAnnotationPresent(ToolParameter.class)).toList();
        List<String> parameterDescription = new ConcurrentLinkedList<>();
        for (var param : parameters) {
            var parAnnot = param.getAnnotation(ToolParameter.class);
            var parName = parAnnot.name();
            var parType = param.getType().getSimpleName();

            var parExplained = "--- '" + parName + "' of type '" + parType + "'";
            if (!Utils.isEmpty(parAnnot.description())) {
                parExplained += " used for: " + parAnnot.description();
            }

            parameterDescription.add(parExplained);
        }

        return "'" + name + "' with the parameters:\n" + String.join("\n", parameterDescription);
    }

    public String getTools(String... toolNames) {
        return Arrays.stream(ToolsFunction.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(ToolName.class) && Arrays.stream(toolNames).anyMatch(it -> it.equalsIgnoreCase(m.getAnnotation(ToolName.class).name())))
                .map(this::getToolDescription).collect(Collectors.joining("\n\n"));
    }
}
