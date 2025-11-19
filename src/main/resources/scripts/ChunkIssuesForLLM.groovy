import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext
import com.capco.brsp.synthesisengine.utils.*

class ChunkIssuesForLLM implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        // Get chunkSize from configs or use default value
        def chunkSize = projectContext['$api']?.configs?.options?.chunkSize ?: 10
        def issues = projectContext.normalizedIssues ?: []

        if (issues.isEmpty()) {
            projectContext.put("issueChunks", [])
            return null
        }

        def chunks = []
        def currentChunk = []

        issues.eachWithIndex { issue, index ->
            currentChunk.add(issue)

            if (currentChunk.size() >= chunkSize || index == issues.size() - 1) {
                chunks.add([
                    chunkIndex: chunks.size(),
                    chunkSize: currentChunk.size(),
                    issues: new ArrayList(currentChunk)
                ])
                currentChunk.clear()
            }
        }

        // Set in projectContext using put()
        projectContext.put("issueChunks", chunks)

        // Return null since we already set the context
        return null
    }
}
