# Spring AI and MCP in Code Factory — Recipes Guide

This document explains, end-to-end, how Code Factory uses Spring AI and the MCP Client, and how you can control everything from recipes. It covers:

- Agent configuration (via environment variables and via recipe)
- How recipes invoke LLMs and tools (directives)
- Conversation memory management (sessions, summaries)
- Chunking and summarization utilities
- MCP Client (SSE/STDIO) and the Monolith Decomposition flow
- Storage locations and artifacts
- Troubleshooting and best practices

The examples below reflect the current codebase (Spring Boot + Spring AI) and the repository layout.


## 1) Agents — configuration and resolution order

There are two ways to configure an agent (AgentDto):

1) Environment variables (.env) — recommended for environment-specific defaults.
   - Variables follow the convention `AGENT_<ALIAS>_<FIELD>` (for example, `AGENT_AZURE_MODEL`, `AGENT_BEDROCK_TEMPERATURE`).
   - On startup, `AgentEnvLoader` reads the `.env`, expands `${...}` placeholders with values from the same `.env` (or OS env), and creates AgentDto entries.
   - `AgentRegistryService` validates and registers agents by their `name` (alias). You will see a log like:
     - `Loaded N agent(s) from environment: [AZURE, BEDROCK, ...]`
     - `Registered agent AZURE with key AZURE`

2) Recipe configuration (`config.agents`) — useful to override or define per-recipe settings.
   - You can provide a minimal agent in the recipe with only the `name` to pick up the preset from `.env`:
     ```yaml
     config:
       agents:
         - name: AZURE
     ```
   - Or provide overrides. When `name` is present, the system merges your overrides into the preset from `.env`:
     ```yaml
     config:
       agents:
         - name: AZURE
           temperature: 0.1
           maxTokens: 12000
           metadata:
             inputMode: url-only
     ```

Resolution order used at runtime:
- If a directive or code asks for an agent by name (e.g., `@@@agent("AZURE")` or `@@@mcp("...","AZURE")`):
  1) Try the registry (agents loaded from `.env`).
  2) If not found, try the recipe (`config.agents`).
  3) If still not found, fall back to a safe default (Azure gpt-4o with reasonable defaults), and a warning is logged.
- If neither the directive nor the recipe specifies an agent, `LLMSpringService.getDefaultConfig()` tries, in order: `AZURE` (registry) → `DEFAULT` (registry) → hardcoded fallback.

Important provider-specific validation:
- `provider = azure` requires `deploymentName`.
- `provider = openai`, `bedrock`, `vertexai` require a `model`.


## 2) Using Spring AI in recipes — directives

Recipes can drive the LLM flow using custom directives interpreted by `ScriptService`. The most relevant ones:

- `@@@agent("<NAME>")` — selects an agent by name and runs the subsequent content with that agent.
  - If files are attached (`$api.files` + fileNames), `promptWithFile` is used; otherwise `prompt`.
  - If you pass only a name, the system resolves it from registry (env) then recipe.

- `@@@mcp("<connection>", "<AGENT_NAME>")` — triggers an MCP-based flow using a specific agent.
  - The handler resolves the agent as described above and persists it in the project context before building and executing the MCP prompt.

- `@@@closellmthread` — ends the current conversation thread so that the next `@@@agent` starts fresh memory.

- `@@@groovy("<script.groovy>")` — executes a Groovy helper as part of the recipe’s lifecycle (e.g., prepare inputs).

Other helper directives you’ll see in recipes include SpEL variants like `@@@_spel(...)` to manipulate the context, repeaters, and validators. Look under `src/main/resources/recipes` for examples.

Project context and files:
- `$api.files["name.ext"]` is used to pass binary/text files to prompts (e.g., MCP inputs, code artifacts). Controllers and services in the project will persist them under `temp\projects\{flowKey}\...` or `data\uploads\...` depending on the flow.


## 3) Conversation memory — sessions, token control, and summaries

`LLMSpringService` maintains in-memory conversation histories keyed by a `conversationId` (UUID per session). Each turn:
- The user prompt and assistant response are appended to `ConversationMemoryDto.activeMessages`.
- Token counts are tracked. When thresholds are exceeded, older messages are summarized and archived to keep the active history within budget.
- Metadata such as provider/model is recorded in the conversation metadata.

Key operations (internal):
- `getOrCreateConversationMemory(conversationId)` — ensures a session exists.
- `addMessageMemory(...)` — appends a user/assistant message, estimating tokens and updating totals.
- `manageMemorySize(...)` — summarizes older messages when token usage crosses a threshold; enforces max tokens in memory by moving or removing old content.

How to reset memory from a recipe:
- Use `@@@closellmthread` before starting a new flow or to avoid contaminating the next turn with previous context.

Where memory is stored:
- In memory (Java process) for speed and simplicity. If you need persistence, you can export histories from the project context (e.g., for audit) or extend the service to a DB-backed store.


## 4) Chunking and summarization utilities

Large inputs can be chunked and processed in parallel:
- `LLMSpringService.splitTextIntoChunks(String text, int chunkSize)` — slices content into chunks (`llm.chunk-size` in `application.yaml`, default is generous for this project). Each chunk can be summarized or processed independently.
- Parallel summarization is available in `LLMSpringService` (per-chunk `CompletableFuture` pipeline) with consolidated output.

HTML helpers:
- `HtmlExtractor.extractVisualChunks(html)` — returns a list of visually separated text blocks.
- `HtmlExtractor.extractStructuredChunks(html)` — returns a structured representation for richer formatting.

Recipe examples:
- `recipes/chunked-docs.yaml`, `recipes/data-taxonomy.yaml`, and `recipes/n-authority.yaml` show how to:
  - Extract and clean up chunks via SpEL helpers.
  - Ask the LLM to plan chunk boundaries or label chunks.
  - Aggregate chunk outputs.


## 5) MCP Client (SSE/STDIO) and Monolith Decomposition flow

What MCP provides here:
- A standardized way for the LLM to call external tools exposed by the MCP Server.
- In the Monolith Decomposition scenario, the MCP Server exposes tools to start a job and fetch progress and final reports.

Transport options:
- **SSE** (HTTP streaming) — point Spring AI MCP Client to `http://<host>:<port>/sse`.
- **STDIO** — start a local process and communicate over stdio (useful for local development).

Configuration (SSE example in `application.yaml`):
```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        sse:
          connections:
            monolith-decomposition-sse:
              url: http://localhost:8097
              sse-endpoint: /sse
              tool-call-timeout: 100s
```

End-to-end Monolith Decomposition via recipe:
1) In your recipe’s `executorEvents.beforeAll`, call:
   ```
   @@@closellmthread
   @@@mcp("monolith-decomposition", "AZURE")
   Make a monolith decomposition using mcp tools.
   @@@groovy("reports-before-all.groovy")
   ```
2) The MCP handler will:
   - Normalize and store the required files (data_matrix.csv, adjacency_matrix.csv, graph_performs_calls.svg) under `data\uploads\monolith\{UUID}`.
   - Generate public URLs based on `APP_PUBLIC_BASE_URL` or `http://localhost:{port}`.
   - Build a prompt instructing the LLM to call the MCP START tool with `job_uid` and to use MCP tools for progress and results.
   - Resolve the agent (AZURE above) from the `.env` registry first; if not found, try `config.agents`; else fallback.
   - Execute the first turn and schedule the follow-up.
3) Follow-up service will:
   - Poll progress using the MCP `get_progress_number` tool until it reaches 100.
   - Prefer a downloadable URL for the final JSON report; if not available, request strict JSON content.
   - Persist outputs under `temp\projects\{flowKey}-mcp\monolith-decomposition\reports`:
     - `monolith_decomposition_report.json` (JSON)
     - `monolith_decomposition_report.pdf` (if provided)
   - Populate `$api.files` so later recipe steps can consume the results.

Tips:
- Prefer URL downloads for large artifacts to reduce token usage and avoid truncation/timeouts.
- Use an agent with `metadata.mcpTools` to allow only the required MCP tools during this step.


## 6) Storage locations and artifacts

- Uploaded inputs: `data\uploads\monolith\{UUID}\...`
- Final reports (MCP): `temp\projects\{flowKey}-mcp\monolith-decomposition\reports\...`
- Conversation memory: in-process (Java memory). You can export snapshots to your own storage if needed.
- Debug logs: `spring-shell.log` and the application console output.


## 7) Troubleshooting

- Agent not found when using `@@@agent("NAME")` or `@@@mcp("...","NAME")`:
  - Check startup logs that the agent was registered from `.env`.
  - Ensure your `.env` contains `AGENT_<NAME>_PROVIDER` and the provider-specific required fields (e.g., Azure `DEPLOYMENT_NAME`).
  - If not in `.env`, define it in `config.agents` in the recipe.

- Azure `temperature` or other numeric fields appear as `null` at runtime:
  - Ensure your `.env` values are set (and not empty strings). The code applies safe defaults, but validation may fail if critical fields are missing.

- Bedrock 403 “no access to model”:
  - Use canonical model IDs (e.g., `anthropic.claude-3-5-sonnet-20241022-v2:0`, without region prefix) and enable model access in AWS Bedrock for the configured region.

- MCP returns 404 for report URL:
  - Make sure your app exposes the final artifact path (or adjust MCP Server to host it) and that `APP_PUBLIC_BASE_URL` points to a reachable host.

- Long responses truncated / timeouts:
  - Prefer URL downloads for large artifacts.
  - Increase `maxTokens` for the agent used to fetch the final JSON if you must get inline JSON.


## 8) Quick reference — environment variables

General:
- `PROFILE_ACTIVE`, `APP_PUBLIC_BASE_URL`, `PORT`, `VERSION`

Azure OpenAI:
- `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT_NAME`

Bedrock:
- `AWS_REGION`, `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`
- Embeddings: `AWS_BEDROCK_TITAN_EMBEDDING_MODEL`, `AWS_BEDROCK_COHERE_EMBEDDING_MODEL`

Vertex AI:
- `GOOGLE_APPLICATION_CREDENTIALS`, `VERTEX_AI_PROJECT_ID`, `VERTEX_AI_LOCATION`, `GOOGLE_CLOUD_GEMINI_MODEL_NAME`

Agents via `.env` (examples):
```
AGENT_AZURE_NAME=AZURE
AGENT_AZURE_PROVIDER=azure
AGENT_AZURE_MODEL=gpt-4o
AGENT_AZURE_DEPLOYMENT_NAME=${AZURE_OPENAI_DEPLOYMENT_NAME}
AGENT_AZURE_TEMPERATURE=0.2
AGENT_AZURE_MAX_TOKENS=8000
AGENT_AZURE_METADATA_JSON={"inputMode":"url-only","mcpTools":["spring_ai_mcp_client_monolith_decomposition_sse_get_report_json","spring_ai_mcp_client_monolith_decomposition_sse_get_report_pdf","spring_ai_mcp_client_monolith_decomposition_sse_get_progress_number"]}

AGENT_BEDROCK_NAME=BEDROCK
AGENT_BEDROCK_PROVIDER=bedrock
AGENT_BEDROCK_MODEL=anthropic.claude-3-5-sonnet-20241022-v2:0
AGENT_BEDROCK_TEMPERATURE=0.5
AGENT_BEDROCK_MAX_TOKENS=4000
```


## 9) Minimal end-to-end recipe example

```yaml
name: Monolith Decomposition via MCP
config:
  # Optional: define/override agents here. If omitted, @@@mcp and @@@agent will resolve from .env
  agents:
    - name: AZURE               # will merge with the preset from .env
      metadata:
        inputMode: url-only

executor: ProjectModelExecutor.java
executorEvents:
  beforeAll: |-
    @@@closellmthread
    @@@mcp("monolith-decomposition", "AZURE")
    Make a monolith decomposition using mcp tools.
    @@@groovy("reports-before-all.groovy")
```

Outputs are saved under `temp\projects\{flowKey}-mcp\monolith-decomposition\reports` and exposed via `$api.files`.

---

If you need additional provider-specific examples, see `src/main/resources/recipes` and `application.yaml`. For questions or contributions, open a ticket in this repository.
