package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * ExtractParentFields.groovy
 *
 * Extracts parent.key and parent.fields.issuetype.name from Jira API response
 * BEFORE serialization to avoid circular reference issues.
 *
 * This script processes the raw Java Map object returned by @@@api,
 * extracts specific parent fields, and adds them to each issue at root level.
 */
class ExtractParentFields implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        return execute(applicationContext, projectContext, new Object[0])
    }

    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def rawResponse = params.length > 0 ? params[0] : null

        if (rawResponse == null) {
            println "[ExtractParentFields] ERROR: No input data provided"
            return [:]
        }

        println "[ExtractParentFields] Starting parent field extraction..."
        println "[ExtractParentFields] Input type: ${rawResponse.getClass().name}"

        // Clone the response to avoid modifying original
        def response = rawResponse instanceof Map ? new LinkedHashMap(rawResponse) : rawResponse

        // Process issues array
        def issues = response.issues ?: []
        println "[ExtractParentFields] Processing ${issues.size()} issues"

        def extractedCount = 0
        issues.eachWithIndex { issue, index ->
            try {
                def fields = issue.fields
                def parent = fields?.parent

                if (parent != null) {
                    // Extract parent.key
                    def parentKey = parent.key

                    // Extract parent.fields.issuetype.name
                    def parentIssueType = parent.fields?.issuetype?.name

                    if (parentKey != null) {
                        // Add extracted fields at root level of issue
                        issue.parentKey = parentKey
                        issue.parentIssueType = parentIssueType ?: ''

                        extractedCount++

                        if (extractedCount <= 3) {
                            println "[ExtractParentFields] Issue #${index} (${issue.key}): extracted parentKey='${parentKey}', parentIssueType='${parentIssueType}'"
                        }
                    }
                } else {
                    // No parent - add empty fields
                    issue.parentKey = ''
                    issue.parentIssueType = ''
                }
            } catch (Exception e) {
                println "[ExtractParentFields] WARNING: Failed to extract parent from issue #${index}: ${e.message}"
            }
        }

        println "[ExtractParentFields] Successfully extracted parent fields from ${extractedCount} issues"
        return response
    }
}
