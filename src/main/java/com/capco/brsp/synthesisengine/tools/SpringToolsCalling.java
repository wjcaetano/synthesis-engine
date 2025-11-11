package com.capco.brsp.synthesisengine.tools;

import com.bazaarvoice.jolt.Chainr;
import com.capco.brsp.synthesisengine.dto.AgentEmbConfigDto;
import com.capco.brsp.synthesisengine.service.ContextService;
import com.capco.brsp.synthesisengine.service.LLMEmbeddingSpringService;
import com.capco.brsp.synthesisengine.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.util.FileUtil;
import org.jline.nativ.OSInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.capco.brsp.synthesisengine.utils.FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH;

@Slf4j
@Component
public class SpringToolsCalling {
    private final ContextService contextService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final LLMEmbeddingSpringService embeddingService;

    public SpringToolsCalling(LLMEmbeddingSpringService embeddingService, ContextService contextService) {
        this.embeddingService = embeddingService;
        this.contextService = contextService;
    }

    @Tool(description = "Get the embedding vector for a given text using the specified embedding model config", name = "getEmbedding")
    public float[] getEmbedding(String text, @ToolParam(required = false) String provider) {
        AgentEmbConfigDto config = new AgentEmbConfigDto();

        if (provider == null || provider.isEmpty()) {
            config.setProvider("bedrock-titan");
        } else {
            config.setProvider(provider);
        }

        config.setInputType("TEXT");
        float[][] result = embeddingService.promptEmbeddingAsArray(text, config);
        return result.length > 0 ? result[0] : new float[0];
    }

    @Tool(description = "Prints the given text on the console", name = "printText")
    public void soutTool(String text) {
        System.out.println(text);
    }

    // -------------------------------
    // Web search tools
    // -------------------------------

    @Tool(
            name = "arxiv",
            description = "Search arXiv and return parsed search results. Parameters: 'query' (search terms), 'maxResults' (limit)."
    )
    public Object arxiv(
            @ToolParam(description = "Search query string") String query,
            @ToolParam(description = "Maximum number of results to return") int maxResults
    ) throws Exception {
        String cleanedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://arxiv.org/search/?query=" + cleanedQuery + "&searchtype=all&source=header";
        return WebSearchUtils.parseFromUrl(url, maxResults);
    }

    @Tool(
            name = "bing",
            description = "Search Bing and return parsed search results. Parameters: 'query' (search terms), 'maxResults' (limit)."
    )
    public Object bing(
            @ToolParam(description = "Search query string") String query,
            @ToolParam(description = "Maximum number of results to return") int maxResults
    ) throws Exception {
        String cleanedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.bing.com/search?q=" + cleanedQuery + "&cc=US&setlang=en-US&count=50";
        return WebSearchUtils.parseFromUrl(url, maxResults);
    }

    @Tool(
            name = "duckduckgo",
            description = "Search DuckDuckGo and return parsed search results. Parameters: 'query' (search terms), 'maxResults' (limit)."
    )
    public Object duckDuckGo(
            @ToolParam(description = "Search query string") String query,
            @ToolParam(description = "Maximum number of results to return") int maxResults
    ) throws Exception {
        String cleanedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://duckduckgo.com/?t=h_&q=" + cleanedQuery;
        return WebSearchUtils.parseFromUrl(url, maxResults);
    }

    // -------------------------------
    // Generic HTTP tool
    // -------------------------------

    @Tool(
            name = "api_call",
            description = "Make an HTTP request and return the response as JSON (if JSON) or String. " +
                    "Parameters: 'url', optional 'method' (GET default), optional 'body', optional 'headers'."
    )
    public Object apiCall(
            @ToolParam(description = "Absolute URL to call") String url,
            @ToolParam(required = false, description = "HTTP method, default GET") String method,
            @ToolParam(required = false, description = "Request body (serialized as JSON if present)") Object body,
            @ToolParam(required = false, description = "Map of HTTP headers") Map<String, String> headers
    ) {
        if (method == null) method = "GET";
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

    // -------------------------------
    // Data transform
    // -------------------------------

    @Tool(
            name = "jolt",
            description = "Apply a Jolt spec transform to an input (JSON object or JSON string). " +
                    "Parameters: 'input', 'spec' (Jolt spec JSON string)."
    )
    public Object jolt(
            @ToolParam(description = "Input as Object or JSON string") Object input,
            @ToolParam(description = "Jolt spec as JSON string") String spec
    ) throws JsonProcessingException {
        Object inputObj = input instanceof String inputString
                ? JsonUtils.readAsObject(inputString, new ConcurrentLinkedHashMap<>())
                : input;
        Object specMap = JsonUtils.readAsObject(spec, new ConcurrentLinkedList<>());

        Chainr chainr = Chainr.fromSpec(specMap);
        return chainr.transform(inputObj);
    }

    // -------------------------------
    // Confluence publishing
    // -------------------------------

    @Tool(
            name = "confluence",
            description = "Publish a set of HTML pages to Confluence under a parent page. " +
                    "Deletes existing children first. Parameters: 'documents' (name->HTML), 'user', 'token', 'url' (REST API base), 'folderID' (parent page ID), 'spaceKey'."
    )
    public String confluence(
            @ToolParam(description = "Map of filename->Confluence storage-format HTML") Map<String, String> documents,
            @ToolParam(description = "Confluence username/email") String user,
            @ToolParam(description = "Confluence API token") String token,
            @ToolParam(description = "Confluence REST content endpoint, e.g., https://<site>/wiki/rest/api/content") String url,
            @ToolParam(description = "Parent page ID where content will be created") String folderID,
            @ToolParam(description = "Confluence space key") String spaceKey
    ) {
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

    // -------------------------------
    // Shell and filesystem utilities
    // -------------------------------

    @Tool(
            name = "shell_run",
            description = "Run a shell command inside the flow's working folder. Parameters: 'os' (hint, e.g., 'windows' or 'unix'), 'command' (string). Returns stdout."
    )
    public String shellRun(
            @ToolParam(description = "OS hint, e.g., 'windows' or 'unix'") String os,
            @ToolParam(description = "Command to execute") String command
    ) throws IOException, InterruptedException {
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

    @Tool(
            name = "glob",
            description = "List files matching a glob-like pattern, sorted by last modified (desc). Parameter: 'pattern'."
    )
    public String globTool(
            @ToolParam(description = "Glob-like path pattern (platform-specific handling)") String pattern
    ) throws IOException, InterruptedException {
        boolean isWindows = OSInfo.getOSName().toLowerCase().contains("windows");

        String command;
        if (isWindows) {
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

    @Tool(
            name = "grep",
            description = "Recursive grep by regex, restricted to files matching 'include' pattern. Parameters: 'regex', 'include'."
    )
    public String grepTool(
            @ToolParam(description = "Regular expression to search") String regex,
            @ToolParam(description = "Filename include pattern (e.g., *.java)") String includePattern
    ) throws IOException, InterruptedException {
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

    @Tool(
            name = "ls",
            description = "List files under a path, optionally ignoring globs. Parameters: 'path' (absolute), optional 'ignore' (list of globs)."
    )
    public String lsTool(
            @ToolParam(description = "Absolute path to list") String absolutePath,
            @ToolParam(required = false, description = "List of globs to ignore") List<String> ignoreGlobs
    ) throws IOException, InterruptedException {
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

    @Tool(
            name = "view",
            description = "View the first N lines of a file. Parameters: 'file_path' (absolute), optional 'max_lines' (default 2000)."
    )
    public String viewFile(
            @ToolParam(description = "Absolute file path") String absoluteFilePath,
            @ToolParam(required = false, description = "Max lines to read (default 2000)") Integer maxLines
    ) throws IOException, InterruptedException {
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

    @Tool(
            name = "replace_in_file",
            description = "Replace all occurrences of a string in a file and write back. " +
                    "Parameters: 'file_path', 'original_value', 'new_value'. Returns a summary with the new content."
    )
    public String replaceInFile(
            @ToolParam(description = "Absolute file path") String absoluteFilePath,
            @ToolParam(description = "String to search for") String originalValue,
            @ToolParam(description = "Replacement string") String newValue
    ) throws IOException, InterruptedException {
        var filePath = Paths.get(absoluteFilePath);
        var file = new File(absoluteFilePath);
        var fileContent = FileUtil.readAsString(file);

        if (fileContent.contains(originalValue)) {
            fileContent = fileContent.replace(originalValue, newValue);
            FileUtils.writeFile(filePath, fileContent, false);
            return "Replaced all occurrences of '" + originalValue + "' with '" + newValue + "' in file: " + absoluteFilePath +
                    "\n The new content looks like below:\n\n[CONTENT]\n" + fileContent;
        } else {
            return "Maybe the original_value has something wrong because as no occurrences of '" + originalValue +
                    "' found in file: " + absoluteFilePath;
        }
    }

    @Tool(
            name = "new_file",
            description = "Create or overwrite a file with the given value. Parameters: 'file_path', 'value'."
    )
    public String newFile(
            @ToolParam(description = "Absolute file path to write") String absoluteFilePath,
            @ToolParam(description = "Content to write") String value
    ) {
        var filePath = Paths.get(absoluteFilePath);
        FileUtils.writeFile(filePath, value, false);
        return "Value was written to the file: " + absoluteFilePath;
    }
}
