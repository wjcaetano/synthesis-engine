package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * ExtractParentFields.groovy
 *
 * Extracts parent.key and parent.fields.issuetype.name from Jira API response
 * BEFORE serialization to avoid circular reference issues.
 *
 * Processes raw Java Map from @@@api, extracts parent fields, and adds them
 * to each issue at root level (parentKey, parentIssueType).
 */
class ExtractParentFields implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        return execute(applicationContext, projectContext, new Object[0])
    }

    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def response = params.length > 0 ? params[0] : null

        if (response == null) {
            println "[ExtractParentFields] ERROR: No input data provided"
            return [:]
        }

        // Handle both full API response {issues: [...]} and direct list
        def issues = response instanceof Map ? (response.issues ?: []) :
                     response instanceof List ? response : []

        if (issues.isEmpty()) {
            println "[ExtractParentFields] No issues to process"
            return response
        }

        def extractedCount = 0
        issues.each { issue ->
            if (issue instanceof Map) {
                try {
                    // Access parent field (in Jira API it's under fields.parent)
                    def parent = issue.fields?.parent

                    if (parent instanceof Map) {
                        def parentKey = parent.key
                        def parentIssueType = parent.fields?.issuetype?.name

                        if (parentKey) {
                            issue.parentKey = parentKey
                            issue.parentIssueType = parentIssueType ?: ''
                            extractedCount++
                        }
                    }
                } catch (Exception e) {
                    println "[ExtractParentFields] WARNING: Failed to extract from ${issue.key ?: 'unknown'}: ${e.message}"
                }
            }
        }

        println "[ExtractParentFields] Extracted parent fields from ${extractedCount}/${issues.size()} issues"
        return response
    }
}
