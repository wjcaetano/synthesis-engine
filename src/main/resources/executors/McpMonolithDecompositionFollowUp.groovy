package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class McpMonolithDecompositionFollowUp implements IExecutor {
    Object execute(ApplicationContext ctx, Map projectContext) {
        def scriptService = ctx.getBean(ScriptService)

        String uid = Utils.convertTo(projectContext.get('monolith.uid'), String.class, null)
        if (!uid) {
            String lastContent = Utils.convertTo(projectContext.get('content'), String.class, null)
            if (!lastContent) {
                throw new IllegalStateException("No previous LLM content found to extract UID from and monolith.uid is missing.")
            }

            def uidMatcher = (lastContent =~ /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/)
            if (!uidMatcher.find()) {
                throw new IllegalStateException("Could not find a UID in the previous LLM response and monolith.uid is missing. Content: \n" + lastContent)
            }
            uid = uidMatcher.group(0)
            projectContext['monolith.uid'] = uid
        }


        UUID projectUUID = projectContext['projectUUID'] as UUID
        String contextKey = (projectContext['contextKey'] ?: 'default') as String
        String flowKey = Utils.combinedKey(projectUUID, contextKey)

        Path baseDir = FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH
        Path targetDir = baseDir.resolve(flowKey).resolve('monolith').resolve('report')
        Files.createDirectories(targetDir)

        Path reportJsonFile = targetDir.resolve('monolith_decomposition_report.json')


        int attempts = (projectContext['monolith.maxAttempts'] instanceof Number) ? ((Number) projectContext['monolith.maxAttempts']).intValue() : 600
        int baseWaitMillis = (projectContext['monolith.waitMillis'] instanceof Number) ? ((Number) projectContext['monolith.waitMillis']).intValue() : 5000
        int maxWaitMillis = 15000
        int stagnantThreshold = 20

        Integer lastProgress = null
        int stagnantCount = 0

        for (int i = 0; i < attempts; i++) {
            String progressPrompt = """
You are tracking an ongoing monolith decomposition job.
Use ONLY the MCP monolith tools to check the current numeric progress (0..100) for UID: ${uid}.
Return ONLY the number (no text, no percent sign, no code fences).
If it is completed, return 100.
""".stripIndent()

            String progressResult = scriptService.handleAgent(projectContext, progressPrompt, null)

            Integer progress = extractProgress(progressResult)
            if (progress != null) {
                Map monolith = (projectContext['monolith'] instanceof Map) ? (Map) projectContext['monolith'] : new LinkedHashMap<>()
                monolith['uid'] = uid
                monolith['progress'] = progress
                projectContext['monolith'] = monolith

                if (lastProgress != null && progress.equals(lastProgress)) {
                    stagnantCount++
                } else {
                    stagnantCount = 0
                }
                lastProgress = progress

                if (progress >= 100 || stagnantCount >= stagnantThreshold) {
                    String fetchPrompt = """
The process for UID: ${uid} should now be complete.
Use ONLY the MCP tools to fetch the FINAL results.
Return STRICTLY the JSON report as a fenced block: ```json ... ```
No explanations, no extra text.
""".stripIndent()
                    String fetchResult = scriptService.handleAgent(projectContext, fetchPrompt, null, 5)

                    String jsonBlock = extractJsonBlock(fetchResult)

                    if (jsonBlock != null) {
                        persistOutputs(projectContext, uid, jsonBlock, reportJsonFile)
                        return "Monolith decomposition finished and results persisted in: ${reportJsonFile}"
                    }
                }
            }

            int factor = i + (i / 50)
            long computed = (long) baseWaitMillis * (long) Math.max(factor, 1)
            int waitMillis = (int) Math.min(computed, (long) maxWaitMillis)
            waitMillis = Math.max(waitMillis, 250)
            try {
                Thread.sleep(waitMillis)
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt()
            }
        }

        return "Monolith decomposition still in progress. Last known progress: ${lastProgress != null ? lastProgress + '%' : 'unknown'}."
    }

    private static Integer extractProgress(String text) {
        if (text == null) return null
        String t = text.trim()

        def onlyNumber = (t =~ /^(?:\D*?)(\d{1,3})(?:\D*?)$/)
        if (onlyNumber.find()) {
            try {int v = Integer.parseInt(onlyNumber.group(1))
                if (v >= 0 && v <= 100) return v
            } catch (ignored) {}
        }

        def progressName = (text =~ /(?i)progress\D{0,10}(\d{1,3})\s*%?/)
        if (progressName.find()) {
            try {int v = Integer.parseInt(progressName.group(1))
                if (v >= 0 && v <= 100) return v
            } catch (ignored) {}
        }

        def withPercent = (text =~ /(\d{1,3})\s*%/)
        if (withPercent.find()) {
            try {int v = Integer.parseInt(withPercent.group(1))
                if (v >= 0 && v <= 100) return v
            } catch (ignored) {}
        }

        def anyNumber = (text =~ /(\d{1,3})/)
        Integer best = null
        while (anyNumber.find()) {
            try{
                int v = Integer.parseInt(anyNumber.group(1))
                if (v >= 0 && v <= 100) best = (best == null) ? v : Math.max(best, v)
            } catch (ignored) {}
        }
        return best
    }

    private static String extractJsonBlock(String text) {
        if (text == null) return null
        def jsonMatcher = (text =~ /```json\s*([\s\S]*?)\s*```/)
        return jsonMatcher.find() ? jsonMatcher.group(1).trim() : null
    }


    private static void persistOutputs(Map pc,
                                       String uid,
                                       String jsonBlock,
                                       Path reportJsonFile) {
        Object reportJson
        try {
            reportJson = JsonUtils.readAsObject(jsonBlock, Object)
        } catch (Throwable t) {
            reportJson = jsonBlock
        }

        String jsonOut = (reportJson instanceof String)
                ? (String) reportJson
                : JsonUtils.writeAsJsonString(reportJson, true)

        Files.writeString(reportJsonFile, jsonOut, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        Map monolith = (pc['monolith'] instanceof Map) ? (Map) pc['monolith'] : new LinkedHashMap<>()
        monolith['uid'] = uid
        monolith['reportJson'] = reportJson
        monolith['reportJsonPath'] = "monolith/report/monolith_decomposition_report.json"
        pc['monolith'] = monolith

        Map api = (pc['$api'] instanceof Map) ? (Map) pc['$api'] : new LinkedHashMap<>()
        Map files = (api['files'] instanceof Map) ? (Map) api['files'] : new LinkedHashMap<>()
        files['monolith_decomposition_report.json'] = Utils.encodeBase64(jsonOut)
        api['files'] = files
        pc['$api'] = api
     }
}
