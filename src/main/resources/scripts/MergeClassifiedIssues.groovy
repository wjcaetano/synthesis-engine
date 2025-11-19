import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * MergeClassifiedIssues.groovy
 *
 * Merges LLM classification results back into normalized issues.
 * Takes classifiedIssues (with LLM fields) and normalizedIssues (with full data)
 * and combines them by issueKey.
 */
class MergeClassifiedIssues implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def normalizedIssues = projectContext.normalizedIssues
        def llmClassifications = projectContext.llmClassifications

        if (normalizedIssues == null || normalizedIssues.isEmpty()) {
            projectContext.put("classifiedIssues", [])
            return null
        }

        // If no LLM classifications, just use normalized issues as-is
        if (llmClassifications == null || llmClassifications.isEmpty()) {
            projectContext.put("classifiedIssues", normalizedIssues)
            return null
        }

        println "[MergeClassifiedIssues] Merging ${llmClassifications.size()} LLM classifications with ${normalizedIssues.size()} normalized issues"

        // Create a map of LLM classifications by issueKey for quick lookup
        def llmMap = [:]
        llmClassifications.each { classification ->
            def key = classification.issueKey
            if (key) {
                llmMap[key] = classification
            }
        }

        println "[MergeClassifiedIssues] Created LLM lookup map with ${llmMap.size()} entries"

        // Merge: start with normalized issues and add LLM fields
        def mergedIssues = []
        normalizedIssues.each { issue ->
            def issueKey = issue.issueKey
            def llmData = llmMap[issueKey]

            // Create a new map by explicitly copying each field (avoid putAll on immutable collections)
            def merged = [
                // Core fields
                issueKey: issue.issueKey,
                issueId: issue.issueId,
                summary: issue.summary,
                description: issue.description,
                status: issue.status,
                issueType: issue.issueType,
                priority: issue.priority,
                createdDate: issue.createdDate,
                updatedDate: issue.updatedDate,
                resolutionDate: issue.resolutionDate,
                storyPoints: issue.storyPoints,

                // Nested objects
                assignee: issue.assignee,
                reporter: issue.reporter,
                parent: issue.parent,

                // Arrays/Lists
                components: issue.components,
                labels: issue.labels,
                issueLinks: issue.issueLinks,

                // Changelog
                changeHistory: issue.changeHistory,
                dailyStatusChanges: issue.dailyStatusChanges,

                // Other fields
                timeTracking: issue.timeTracking
            ]

            // Add LLM fields
            if (llmData) {
                merged.llmTechnicalArea = llmData.llmTechnicalArea ?: ''
                merged.llmComplexity = llmData.llmComplexity ?: ''
                merged.llmBusinessImpact = llmData.llmBusinessImpact ?: ''
                merged.llmRiskLevel = llmData.llmRiskLevel ?: ''
                merged.llmRiskFactors = llmData.llmRiskFactors ?: []
            } else {
                // No LLM data for this issue, use default values
                merged.llmTechnicalArea = ''
                merged.llmComplexity = ''
                merged.llmBusinessImpact = ''
                merged.llmRiskLevel = ''
                merged.llmRiskFactors = []
            }

            mergedIssues.add(merged)
        }

        println "[MergeClassifiedIssues] Merged ${mergedIssues.size()} issues"
        if (mergedIssues.size() > 0) {
            println "[MergeClassifiedIssues] First merged issue keys: ${mergedIssues[0].keySet()}"
        }

        projectContext.put("classifiedIssues", mergedIssues)
        return null
    }
}
