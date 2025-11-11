package executors

import com.capco.brsp.synthesisengine.dto.AgentDto
import com.capco.brsp.synthesisengine.mcp.MonolithDecompositionFacade
import com.capco.brsp.synthesisengine.mcp.MonolithDecompositionMcpService
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.FileUtils
import org.springframework.context.ApplicationContext

class McpMonolithDecompositionBeforeAll implements IExecutor {
    private static final String EXECUTORS_DIR = FileUtils.pathJoin("src","main", "resources", "executors").toString()
    private static final String FOLLOW_UP_SCRIPT_NAME = "McpMonolithDecompositionFollowUp.groovy"
    private static String followUpScriptPath() {
        return FileUtils.pathJoin(EXECUTORS_DIR, FOLLOW_UP_SCRIPT_NAME).toString()
    }

    Object execute(ApplicationContext ctx, Map projectContext) {
        def files = projectContext['$api']?.files
        if (!(files instanceof Map) || files.isEmpty()) {
            throw new IllegalStateException("No files found in the context")
        }

        String rawDataCsv = files['data_matrix.csv'] as String
        String rawAdjCsv = files['adjacency_matrix.csv'] as String
        String rawSvg = files['graph_performs_calls.svg'] as String

        if (!rawDataCsv || !rawAdjCsv || !rawSvg) {
            throw new IllegalStateException("Required files (data_matrix.csv, adjacency_matrix.csv, graph_performs_calls.svg) are missing to process the monolith decomposition.")
        }
        String uid = UUID.randomUUID().toString()

        def service = ctx.getBean(MonolithDecompositionMcpService)
        def dataCsv = service.normalizeFileText(rawDataCsv)
        def adjCsv = service.normalizeFileText(rawAdjCsv)
        def svg = service.normalizeFileText(rawSvg)

        def facade = ctx.getBean(MonolithDecompositionFacade)
        def dto = facade.prepare(dataCsv, adjCsv, svg, uid)

        projectContext['monolith'] = ['inputs': dto.inputs, 'uid': uid]
        projectContext['monolith.uid'] = uid

        String promptWithUid = service.buildMonolithPrompt(dto.inputs, uid)
        projectContext['monolith.prompt'] = promptWithUid

        AgentDto agentDto = resolveAgentFromRecipeOrDefault(projectContext)
        Map meta = (agentDto.getMetadata() instanceof Map) ? (Map) agentDto.getMetadata() : new LinkedHashMap()
        meta.put('inputMode', 'url-only')
        agentDto.setMetadata(meta)

        projectContext['agent'] = agentDto
        def scriptService = ctx.getBean(ScriptService)
        String agentResponse = scriptService.handleAgent(projectContext, promptWithUid, null)

        if (!(agentResponse =~ /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/).find()) {
            projectContext['content'] = uid
        } else {
            projectContext['content'] = agentResponse
        }

        def followUp = scriptService.getGroovyExecutor(followUpScriptPath())
        def result = followUp.execute(ctx, projectContext)

        return "MCP Monolith Decomposition job started. Result: ${result}"
    }

    private static AgentDto resolveAgentFromRecipeOrDefault(Map projectContext) {
        Map recipe = projectContext['$recipe'] instanceof Map ? (Map) projectContext['$recipe'] : [:]
        Map cfg = recipe.get('config') instanceof Map ? (Map) recipe.get('config') : [:]
        Map agentCfg = cfg.get('agent')

        AgentDto agent = new AgentDto()
        if (agentCfg instanceof Map) {
            agent.setName(agentCfg.get('name') as String)
            agent.setProvider(nvlStr(agentCfg.get('provider'), 'azure'))
            agent.setModel(nvlStr(agentCfg.get('model'), 'gpt-4o'))
            agent.setDeploymentName(agentCfg.get('deploymentName') as String)
            agent.setTemperature(agentCfg.get('temperature') instanceof Number ? ((Number) agentCfg.get('temperature')).doubleValue() : null)
            agent.setMaxTokens(agentCfg.get('maxTokens') instanceof Number ? ((Number) agentCfg.get('maxTokens')).intValue() : null)
            agent.setTools(agentCfg.get('tools') instanceof List ? (List<String>) agentCfg.get('tools') : null)
            agent.setMetadata(agentCfg.get('metadata') instanceof Map ? (Map<String, Object>) agentCfg.get('metadata') : null)
        } else {
            agent.setProvider("azure")
            agent.setModel("gpt-4o")
            agent.setTemperature(0.7)
            agent.setDeploymentName('Chatbot')
        }
        return agent
    }

    private static String nvlStr(Object val, String defaultValue) {
        return val instanceof CharSequence && val.toString().trim().length() > 0 ? val.toString() : defaultValue
    }
}