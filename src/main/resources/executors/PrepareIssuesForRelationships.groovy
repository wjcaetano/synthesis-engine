package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * PrepareIssuesForRelationships.groovy
 *
 * Prepares classified issues for relationship extraction by:
 * - Limiting to first 100 issues
 * - Truncating long descriptions
 * - Handling null values
 * - Formatting data for Freemarker templates
 * - Sets 'preparedIssuesForRelationships' directly in projectContext
 */
class PrepareIssuesForRelationships implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def classifiedIssues = projectContext.classifiedIssues

        if (classifiedIssues == null || classifiedIssues.isEmpty()) {
            projectContext.put("preparedIssues", [])
            return null
        }

        // Get parameters or use defaults
        def maxIssues = params.length > 0 ? params[0] as Integer : 100
        def maxDescriptionLength = params.length > 1 ? params[1] as Integer : 800

        // Debug: log what we're processing
        println "[PrepareIssuesForRelationships] classifiedIssues size: ${classifiedIssues.size()}"
        println "[PrepareIssuesForRelationships] classifiedIssues class: ${classifiedIssues.getClass()}"
        if (classifiedIssues.size() > 0) {
            println "[PrepareIssuesForRelationships] First classifiedIssue: ${classifiedIssues[0]}"
            println "[PrepareIssuesForRelationships] First classifiedIssue class: ${classifiedIssues[0].getClass()}"
            if (classifiedIssues[0] instanceof Map) {
                println "[PrepareIssuesForRelationships] First issue keys: ${classifiedIssues[0].keySet()}"
                println "[PrepareIssuesForRelationships] issueKey value: ${classifiedIssues[0].issueKey}"
                println "[PrepareIssuesForRelationships] issueKey class: ${classifiedIssues[0].issueKey?.getClass()}"
            }
        }

        // Limit and prepare issues
        def issuesToProcess = classifiedIssues.size() > maxIssues ?
                classifiedIssues[0..(maxIssues-1)] : classifiedIssues

        println "[PrepareIssuesForRelationships] issuesToProcess size: ${issuesToProcess.size()}"

        def preparedIssues = issuesToProcess.collect { issue ->
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
                    epicKey: epicKey,
                    components: issue.components ? (issue.components instanceof List ? issue.components.join(', ') : issue.components) : 'None',
                    labels: issue.labels ? (issue.labels instanceof List ? issue.labels.join(', ') : issue.labels) : 'None'
            ]
        }

        println "[PrepareIssuesForRelationships] preparedIssues size: ${preparedIssues.size()}"
        if (preparedIssues.size() > 0) {
            println "[PrepareIssuesForRelationships] First prepared: ${preparedIssues[0]}"
        }

        // Set in projectContext using put()
        projectContext.put("preparedIssues", preparedIssues)
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