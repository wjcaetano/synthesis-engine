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
            projectContext.put("preparedIssuesForRelationships", [])
            return []
        }

        // Get parameters or use defaults
        def maxIssues = params.length > 0 ? params[0] as Integer : 100
        def maxDescriptionLength = params.length > 1 ? params[1] as Integer : 800

        // Limit and prepare issues
        def issuesToProcess = classifiedIssues.size() > maxIssues ?
            classifiedIssues[0..(maxIssues-1)] : classifiedIssues

        def preparedIssues = issuesToProcess.collect { issue ->
            [
                issueKey: issue.issueKey ?: 'UNKNOWN',
                summary: issue.summary ?: 'No summary',
                description: truncateDescription(issue.description, maxDescriptionLength),
                epicKey: issue.parent?.key ?: 'No Epic',
                components: issue.components ? (issue.components instanceof List ? issue.components.join(', ') : issue.components) : 'None',
                labels: issue.labels ? (issue.labels instanceof List ? issue.labels.join(', ') : issue.labels) : 'None'
            ]
        }

        // Set in projectContext using put()
        projectContext.put("preparedIssuesForRelationships", preparedIssues)

        return preparedIssues
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
