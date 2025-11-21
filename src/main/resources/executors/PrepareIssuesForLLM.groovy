package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * PrepareIssuesForLLM.groovy
 *
 * Prepares issue data for LLM prompts by:
 * - Truncating long descriptions
 * - Handling null values
 * - Formatting dates
 * - Creating clean, ready-to-use data for Freemarker templates
 */
class PrepareIssuesForLLM implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def chunk = projectContext.chunk

        if (chunk == null || chunk.issues == null) {
            def emptyChunk = [issues: []]
            projectContext.put("preparedChunk", emptyChunk)
            return null
        }

        // Get max description length from params or use default
        def maxDescriptionLength = params.length > 0 ? params[0] as Integer : 500

        def preparedIssues = chunk.issues.collect { issue ->
            // Handle description - might be ArrayList or String
            def description = issue.description
            if (description instanceof List) {
                description = description.findAll { it != null }.join(' ')
            }

            // Determine epic key from parentKey field (if parentIssueType is Epic) or epicLinkField
            def epicKey = 'No Epic'
            if (issue.epicLinkField != null && !issue.epicLinkField.toString().isEmpty()) {
                epicKey = issue.epicLinkField
            } else if (issue.parentKey != null && issue.parentIssueType == 'Epic') {
                epicKey = issue.parentKey
            }

            [
                    issueKey: issue.issueKey ?: 'UNKNOWN',
                    summary: issue.summary ?: 'No summary',
                    description: truncateDescription(description as String, maxDescriptionLength),
                    issueType: issue.issueType ?: 'Unknown',
                    status: issue.status ?: 'Unknown',
                    priority: issue.priority ?: 'Unknown',
                    storyPoints: issue.storyPoints ?: 0,
                    createdDate: issue.createdDate ?: 'N/A',
                    updatedDate: issue.updatedDate ?: 'N/A',
                    assigneeName: issue.assignee?.name ?: 'Unassigned',
                    reporterName: issue.reporter?.name ?: 'Unknown',
                    epicKey: epicKey,
                    components: issue.components?.join(', ') ?: 'None',
                    labels: issue.labels?.join(', ') ?: 'None'
            ]
        }

        def preparedChunk = [
                chunkIndex: chunk.chunkIndex,
                chunkSize: chunk.chunkSize,
                issues: preparedIssues
        ]

        // Set in projectContext using put()
        projectContext.put("preparedChunk", preparedChunk)
    }

    private String truncateDescription(String description, int maxLength) {
        if (description == null || description.trim().isEmpty()) {
            return 'No description'
        }

        if (description.length() <= maxLength) {
            return description
        }

        return description.substring(0, maxLength) + '...'
    }
}