# Synthesis Engine

This repository is used to start the BRSP Synthesis Engine. 
Follow the instructions below to set up and start the system seamlessly.

## Requirements

* Java 21 liberica full

## Installation

### Clone the repository:
```bash
git clone https://{your_username}@bitbucket.org/capco-digital/brsp-synthesis-engine.git
```

### Create .env File:

See .env.sample or ask your colleagues.

*Some recipes may require keys that are not on .env*

### Resolve dependencies:
```bash
mvn clean package
```
*Remember to set Liberica 21 as the JDK*
## Usage

### Required files

Synthesis Engine should be listening at: 

http://localhost:8099/

Some recipes require only the files *map.json*, *recipe.yaml* and a populated database to run Synthesis Engine.

Other may require more files to be uploaded depending on the use case.

### Service Dependencies

Synthesis Engine may need other services to run as:
* Neo4j
* LLM-Gateway
* AskAi Front-end

The docker environment with all the other services may be found on the link below:

https://bitbucket.org/capco-digital/brsp-crawler-environment/src/develop/


# LLM Guide with Spring AI and MCP Client

For a comprehensive, recipe-focused guide, see [Spring Ai Guide](docs/SPRING_AI_AND_MCP_RECIPES_GUIDE.md).

This guide explains end-to-end how Synthesis Engine integrates LLMs using Spring AI, how to enable Embeddings and Tool Callbacks, and how to use the MCP Client (Model Context Protocol) — with emphasis on the Monolith Decomposition flow.

Summary:
- Overview of the LLM architecture
- Supported LLM services (Chat and Embeddings)
- Tool Callbacks (Function/Tool Calling)
- MCP Client (SSE and STDIO): what they are, differences, configuration, and flow
- Required environment variables
- How to use in recipes
- Example: Monolith Decomposition (full flow)
- Tips and troubleshooting

## Overview

Synthesis Engine uses Spring AI to unify integration with multiple AI providers (AWS Bedrock, Azure OpenAI, Vertex AI, etc.). For structured tool invocation there are two main mechanisms:
- Spring AI native Tool Callbacks (for local/HTTP tools);
- MCP Client (Model Context Protocol), allowing the LLM to call tools exposed by an external MCP Server (via SSE or STDIO).

The main configuration lives in src\main\resources\application.yaml and can be parameterized using environment variables.

## Supported LLM services

Chat:
- Bedrock Converse (e.g., Claude 3.5 Sonnet)
- Azure OpenAI (gpt-4o, gpt-4o-mini, etc.)
- OpenAI-compatible (mapped via Azure keys in this project)
- Vertex AI Gemini (Gemini models via Vertex)

Embeddings:
- Bedrock Titan
- Bedrock Cohere (input-type: search-document)
- Azure OpenAI Embeddings

Model options and credentials are defined in application.yaml. See the “Environment variables” section for key examples.

## Tool Callbacks (Function/Tool Calling)

You can restrict which tools an agent may call (allowlist). For MCP flows it is also common to restrict tools to drive the model to the MCP Server’s tools. In code, AgentDto supports the tools list and metadata (e.g., inputMode).

Tips:
- Set agent.tools to limit the available tools.
- Use metadata such as inputMode: url-only to force URL inputs (no base64) when that matters for the MCP server.

## MCP Client (SSE and STDIO)

What is MCP?
- MCP (Model Context Protocol) is a protocol that standardizes how LLMs discover and call external tools and resources hosted by an MCP Server. It defines a schema for capabilities, tool invocation, and streaming messages.

What is SSE?
- SSE (Server-Sent Events) is an HTTP-based, uni-directional streaming channel from server to client. In this context, the Spring AI MCP Client connects to an MCP Server that exposes an /sse endpoint to receive tool call events and results in real time over HTTP.

What is STDIO?
- STDIO is a process-based transport where the MCP Client starts a local process (e.g., mcp.exe running a Python server) and exchanges messages through standard input/output streams.

Differences and when to use each:
- SSE:
  - Use when your MCP Server runs as a web service accessible over HTTP.
  - Easier to deploy remotely and to monitor via network tools.
  - Requires network access and potentially CORS/proxy considerations.
- STDIO:
  - Use for local developer workflows, when the server is a CLI or script.
  - No HTTP server required; simpler for quick iterations.
  - Tied to the machine/process lifecycle where Synthesis Engine runs.

Spring AI MCP Client configuration in application.yaml supports both:
- SSE connections (server exposes /sse);
- STDIO connections (a local mcp.exe process runs the server).

Relevant snippet (SSE):

```
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            monolith-decomposition-sse:
              url: http://localhost:8097
              sse-endpoint: /sse
              tool-call-timeout: 60s
```

STDIO (example commented in application.yaml):
- command: path to mcp.exe (Windows)
- args: ["run", "C:\\path\\to\\mcp_server.py"]

How the connection is chosen: the agent, via ScriptService.handleAgent, will use whichever MCP tools are available according to the active Spring AI configuration. Ensure the MCP Server is reachable (SSE) or executable (STDIO) and exposes the expected tools.

## Environment variables

General app:
- CODE_FACTORY_VERSION: displayed version.
- PROFILE_ACTIVE: Spring profile (prod by default).
- APP_PUBLIC_BASE_URL: public URL (e.g., http://your-host:8099) used to generate file links; if empty, defaults to http://localhost:{server.port}.
- CODE_FACTORY_FRONTEND_URLS: allowed origins in CORS (if needed).
- CODE_FACTORY_CORS_ALLOW_CREDENTIALS: true/false for CORS credentials.

LLM Gateway (if applicable):
- HOST_IP_LLM_GATEWAY, HOST_PORT_LLM_GATEWAY, LLM_GATEWAY_API → compose llm-gateway.base_url.

AWS Bedrock (chat/embeddings):
- AWS_BEDROCK_REGION
- AWS_BEDROCK_ACCESS_KEY
- AWS_BEDROCK_SECRET_KEY
- AWS_BEDROCK_TITAN_EMBEDDING_MODEL (e.g., amazon.titan-embed-text-v1)
- AWS_BEDROCK_COHERE_EMBEDDING_MODEL

Azure OpenAI (chat/embeddings):
- AZURE_OPENAI_API_KEY
- AZURE_OPENAI_ENDPOINT (e.g., https://{resource}.openai.azure.com)
- AZURE_OPENAI_MODEL_NAME (deployment name in Azure, e.g., gpt-4o)

OpenAI-compatible (mapped in the project to Azure):
- Uses AZURE_OPENAI_API_KEY/ENDPOINT/MODEL_NAME as in application.yaml

Vertex AI Gemini:
- GOOGLE_APPLICATION_CREDENTIALS (JSON credentials file)
- VERTEX_AI_PROJECT_ID
- VERTEX_AI_LOCATION (e.g., us-central1)
- GOOGLE_CLOUD_GEMINI_MODEL_NAME

MCP Client (SSE):
- Adjust connection keys (url, sse-endpoint) in application.yaml. If you want to parametrize them, create variables and reference them in YAML (e.g., ${MCP_SSE_URL}).

Server:
- server.port (in application.yaml, defaults to 8099)

## How to use in recipes

- Put agent configurations under config.agents in the recipe. You may have chat agents and an embeddings agent. See src\main\resources\recipes\report-project-recipe.yaml for practical examples.
- For MCP flows, use the @@@mcp("monolith-decomposition","<AGENT_NAME>") transform in executorEvents.beforeAll to prepare files, generate the prompt (with a job UID), set the agent metadata (inputMode=url-only), start the job, and trigger the follow-up handled by the system.

Required files (validated by the executor):
- data_matrix.csv
- adjacency_matrix.csv
- graph_performs_calls.svg

How files are read:
- MonolithDecompositionMcpService.normalizeFileText accepts:
  - Plain text;
  - data URI (data:...;base64,...) → automatically decoded;
  - URL-encoded text → decoded;
  - Raw base64 (without data URI) → decoded.

## Example: Monolith Decomposition (full flow)

Key components:
- @@@mcp("monolith-decomposition") BeforeAll transform
  - Normalizes the 3 files and calls MonolithDecompositionFacade.prepare(...), which:
    - Saves files under data\uploads\monolith\{UUID}
    - Generates public URLs for each file via FilesController (based on APP_PUBLIC_BASE_URL or http://localhost:{server.port})
    - Builds a prompt that instructs the LLM to call the correct MCP tool, including a job_uid (UID)
  - Adjusts AgentDto (e.g., metadata.inputMode = url-only)
  - Fires the agent: ScriptService.handleAgent(projectContext, prompt)
  - Stores the UID in projectContext['monolith.uid'] and triggers the follow-up.

- McpMonolithDecompositionFollowUp.groovy
  - Polls progress using ONLY the MCP tools (0..100).
  - When it reaches 100 (or stagnates), requests:
    1) The JSON report as a fenced block ```json ... ```
    2) The PDF as URL (or base64 data URI)
  - Persists outputs to:
    - temp\projects\{flowKey}\monolith\report\monolith_decomposition_report.json
    - temp\projects\{flowKey}\monolith\report\monolith_decomposition_report.pdf
  - Populates $api.files (for subsequent recipe steps):
    - monolith_decomposition_report.json (base64 of the JSON)
    - monolith_decomposition_report.pdf (data URI or file converted to base64)

- MonolithDecompositionMcpService.java
  - resolveBaseUrl: uses APP_PUBLIC_BASE_URL when set; otherwise http://localhost:{server.port}
  - prepareInputs: creates folder and saves required files; returns public URLs
  - buildMonolithPrompt: instructs the LLM to explicitly call the MCP START tool with exact arguments and to use job_uid for progress and results

- MonolithDecompositionFacade.java
  - High-level API to orchestrate prepare(...) and produce the prompt.

Recipe snippet (reference):
```
executorEvents:
  beforeAll: |-
    @@@mcp("monolith-decomposition","<AGENT_NAME>")
```

Step-by-step summary:
1) Provide the 3 required files to $api.files in the recipe context.
2) Run the recipe that uses @@@mcp("monolith-decomposition","<AGENT_NAME>") in beforeAll.
3) The agent will call the MCP START tool; then the FollowUp will poll progress and fetch final artifacts.
4) Find results under temp\projects\{flowKey}\monolith\report and in $api.files.

## Models and embeddings (quick examples)

- Azure OpenAI Chat: set AZURE_OPENAI_* and define the deployment name in spring.ai.azure.openai.chat.options.deployment-name.
- Bedrock Chat (Converse): set AWS_BEDROCK_* and a model in spring.ai.bedrock.converse.chat.options.model.
- Embeddings:
  - bedrock-titan / bedrock-cohere / azure-openai listed in spring.ai.model.embedding.
  - Choose the provider according to your keys.

## Best practices and troubleshooting

- MCP does not connect (SSE):
  - Check if the MCP server is running at http://localhost:8097 and exposes /sse.
  - Adjust CORS or proxies if necessary.
  - tool-call-timeout: increase for long-running operations.

- The LLM does not call the right tool:
  - Restrict agent.tools to only the relevant MCP tools.
  - Ensure the prompt (buildMonolithPrompt) is used in the first turn.

- Invalid files or weird encoding:
  - Send as data URI (data:text/csv;base64,...) or plain text; normalizeFileText handles base64 and URL-encoding.

- Broken file links:
  - Set APP_PUBLIC_BASE_URL to the public URL used to access Synthesis Engine.

- Validate environment:
  - Test simple chat/embeddings before running the MCP flow to isolate credential issues.

## Quick start (summary)
1) Set environment variables (AWS/Azure/Vertex, APP_PUBLIC_BASE_URL, etc.).
2) Adjust application.yaml if needed (models, MCP SSE/STDIO connections).
3) Provide the 3 files to the recipe and include @@@mcp("monolith-decomposition","<AGENT_NAME>") in beforeAll.
4) Run Synthesis Engine at http://localhost:8099 and watch logs. Final monolith outputs are in temp\projects\{flowKey}\monolith\report and in $api.files.
