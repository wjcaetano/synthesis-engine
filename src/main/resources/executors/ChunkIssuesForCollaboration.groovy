package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * ChunkIssuesForCollaboration.groovy
 *
 * Divides classified issues into smaller chunks for collaboration analysis.
 * This prevents LLM timeouts when analyzing large issue sets.
 *
 * Default chunk size: 50 issues
 */
class ChunkIssuesForCollaboration implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def chunkSize = params.length > 0 ? params[0] as Integer : 50
        def issues = projectContext.classifiedIssues ?: []

        if (issues.isEmpty()) {
            println "[ChunkIssuesForCollaboration] No issues to chunk"
            projectContext.put("collaborationChunks", [])
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

        println "[ChunkIssuesForCollaboration] Created ${chunks.size()} chunks from ${issues.size()} issues (chunk size: ${chunkSize})"
        projectContext.put("collaborationChunks", chunks)
    }
}
