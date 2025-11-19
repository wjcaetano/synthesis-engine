package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

class ChunkIssuesForLLM implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        // Get chunkSize from configs or use default value
        def chunkSize = projectContext['$api']?.configs?.options?.chunkSize ?: 10
        def issues = projectContext.normalizedIssues ?: []

        if (issues.isEmpty()) {
            return []
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
    }
}