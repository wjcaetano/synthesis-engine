import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext
import com.capco.brsp.synthesisengine.utils.*

class ChunkIssuesForLLM implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def chunkSize = params.length > 0 ? params[0] as Integer : 10
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

        return chunks
    }
}
