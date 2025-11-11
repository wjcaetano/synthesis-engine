package com.capco.brsp.synthesisengine.mcp;

import com.capco.brsp.synthesisengine.mcp.dto.MonolithInputsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonolithDecompositionMcpService {

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * Public base URL for generating file links. If absent, falls back to http://localhost:{server.port}
     * Example: http://your-host:8099
     */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Value("${app.monolith.uploads-root:data\\\\uploads\\\\monolith}")
    private String uploadsRoot;

    /**
     * Prepare the required files for the Monolith Decomposition MCP server and return their public URLs.
     * - Creates a unique folder in data\\uploads\\monolith
     * - Writes data_matrix.csv, adjacency_matrix.csv, graph_performs_calls.svg
     * - Generates HTTP URLs that point to FilesController endpoints
     */
    public MonolithInputsDto prepareInputs(String dataMatrixCsv,
                                           String adjacencyMatrixCsv,
                                           String graphPerformsCallsSvg) throws IOException {
        Objects.requireNonNull(dataMatrixCsv, "data_matrix.csv content is required");
        Objects.requireNonNull(adjacencyMatrixCsv, "adjacency_matrix.csv content is required");
        Objects.requireNonNull(graphPerformsCallsSvg, "graph_performs_calls.svg content is required");

        String dataMatrixCsvNormalized = normalizeFileText(dataMatrixCsv);
        String adjacencyMatrixCsvNormalized = normalizeFileText(adjacencyMatrixCsv);
        String graphPerformsCallsSvgNormalized = normalizeFileText(graphPerformsCallsSvg);

        String folder = UUID.randomUUID().toString();
        Path folderPath = Path.of(uploadsRoot, folder);
        Files.createDirectories(folderPath);

        Path dataMatrixPath = folderPath.resolve("data_matrix.csv");
        Path adjacencyPath = folderPath.resolve("adjacency_matrix.csv");
        Path graphSvgPath = folderPath.resolve("graph_performs_calls.svg");

        writeTextFile(dataMatrixPath, dataMatrixCsvNormalized);
        writeTextFile(adjacencyPath, adjacencyMatrixCsvNormalized);
        writeTextFile(graphSvgPath, graphPerformsCallsSvgNormalized);

        String base = resolveBaseUrl();
        String dataMatrixUrl = String.format("%s/files/monolith/%s/%s", base, folder, "data_matrix.csv");
        String adjacencyUrl = String.format("%s/files/monolith/%s/%s", base, folder, "adjacency_matrix.csv");
        String graphUrl = String.format("%s/files/monolith/%s/%s", base, folder, "graph_performs_calls.svg");

        return MonolithInputsDto.builder()
                .dataMatrixUrl(dataMatrixUrl)
                .adjacencyMatrixUrl(adjacencyUrl)
                .graphPerformsCallsUrl(graphUrl)
                .build();
    }

    /**
     * Build a recommended prompt to steer the LLM into calling the "monolith_decomposition" MCP tool.
     * The tool name might differ depending on the server; adjust the name/parameters to your server’s schema.
     */
    public String buildMonolithPrompt(MonolithInputsDto inputs, String uid) {
        return "You must run the monolith decomposition tool with the following files (use URLs, not base64).\n" +
                "You MUST associate the job to this job_uid: " + uid + "\n" +
                "Steps:\n" +
                "1) Start the job with job_uid='" + uid + "' using the provided URLs.\n" +
                "2) Use this job_uid for any progress check (0..100) and for fetching final artifacts.\n" +
                "If the MCP tool supports URL inputs, provide them exactly as below.\n" +
                "Inputs:\n" +
                "- data_matrix_url: " + inputs.getDataMatrixUrl() + "\n" +
                "- adjacency_matrix_url: " + inputs.getAdjacencyMatrixUrl() + "\n" +
                "- graph_performs_calls_url: " + inputs.getGraphPerformsCallsUrl() + "\n" +
                "Return strictly the job UID in the first response, in the first line, and then provide the report in JSON format without extra content.\n\n" +
                "IMPORTANT: You MUST call the monolith decomposition START tool with EXACT arguments: " +
                "{ job_uid: , data_matrix_url: , adjacency_matrix_url: , graph_performs_calls_url: }. " +
                "Do NOT attempt to fetch JSON/PDF before the job is started. If there is a named tool like '...start...' use it explicitly.";
    }

    private String resolveBaseUrl() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "");
        }
        return "http://localhost:" + serverPort;
    }

    private void writeTextFile(Path path, String content) throws IOException {
        File file = path.toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Saved file: {} ({} bytes)", path, file.length());
    }

    public String normalizeFileText(String input) {
        if (input == null) return null;
        String s = input;

        try{
            if (s.startsWith("data:")) {
                int comma = s.indexOf(',');
                if (comma > 0) {
                    String meta = s.substring(0, comma);
                    String data = s.substring(comma + 1);

                    if (meta.contains(";base64")) {
                        byte[] bytes = Base64.getDecoder().decode(data);
                        s = new String(bytes, StandardCharsets.UTF_8);
                    } else {
                        s = URLDecoder.decode(data, StandardCharsets.UTF_8);
                        if (isUrlEncoded(s)) {
                            s = URLDecoder.decode(s, StandardCharsets.UTF_8);
                        }
                    }
                }
            } else if (isUrlEncoded(s)) {
                s = URLDecoder.decode(s, StandardCharsets.UTF_8);
                if (isUrlEncoded(s)) {
                    s = URLDecoder.decode(s, StandardCharsets.UTF_8);
                }
            } else if (isBase64(s)) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(s);
                    s = new String(bytes, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {}
            }
        } catch (Exception e) {
            log.error("Error decoding file text: {}", e.getMessage(), e);
        }

        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        return s;
    }

    private boolean isUrlEncoded(String s) {
        return s != null && s.contains("%") && s.matches(".*%(?i:[0-9a-f]{2}).*");
    }

    private boolean isBase64(String s) {
        return s.length() % 4 == 0 && s.matches("^[A-Za-z0-9+/=\\r\\n]+$");
    }
}
