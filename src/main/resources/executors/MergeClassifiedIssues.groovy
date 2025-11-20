package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * MergeClassifiedIssues.groovy
 *
 * Merges LLM classification results with normalized issues.
 * Handles array of arrays structure from @@@set("llmClassifications[]")
 */
class MergeClassifiedIssues implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        return execute(applicationContext, projectContext, new Object[0])
    }
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def normalizedIssues = projectContext.normalizedIssues
        def llmClassifications = projectContext.llmClassifications

        println "[MergeClassifiedIssues] Starting merge..."
        println "[MergeClassifiedIssues] normalizedIssues: ${normalizedIssues?.size() ?: 0} issues"
        println "[MergeClassifiedIssues] llmClassifications: ${llmClassifications?.size() ?: 0} chunks"

        // Handle empty data
        if (normalizedIssues == null || normalizedIssues.isEmpty()) {
            println "[MergeClassifiedIssues] No normalized issues found, returning empty array"
            projectContext.put("classifiedIssues", [])
            return null
        }

        // Flatten llmClassifications (array of arrays -> flat list)
        def flattenedClassifications = []
        if (llmClassifications != null && !llmClassifications.isEmpty()) {
            llmClassifications.each { chunk ->
                if (chunk instanceof List) {
                    flattenedClassifications.addAll(chunk)
                } else {
                    println "[MergeClassifiedIssues] WARNING: Unexpected chunk type: ${chunk?.getClass()}"
                }
            }
        }
        println "[MergeClassifiedIssues] Flattened ${flattenedClassifications.size()} classifications"

        // Create lookup map by issueKey
        def llmMap = [:]
        flattenedClassifications.each { classification ->
            if (classification?.issueKey) {
                llmMap[classification.issueKey] = classification
            }
        }
        println "[MergeClassifiedIssues] Created lookup map with ${llmMap.size()} entries"

        // Merge normalizedIssues with LLM classifications
        def mergedIssues = normalizedIssues.collect { issue ->
            def issueKey = issue.issueKey
            def llmData = llmMap[issueKey] ?: [:]

            // Create merged issue with all original fields + LLM fields
            def merged = [
                    // Core fields
                    issueKey: issueKey,
                    issueId: issue.issueId,
                    summary: issue.summary,
                    description: issue.description,
                    status: issue.status,
                    issueType: issue.issueType,
                    priority: issue.priority,
                    createdDate: issue.createdDate,
                    updatedDate: issue.updatedDate,
                    resolutionDate: issue.resolutionDate,
                    storyPoints: issue.storyPoints ?: 0,

                    // User references
                    assignee: issue.assignee,
                    reporter: issue.reporter,

                    // Relationships
                    parent: issue.parent,

                    // Collections
                    components: issue.components ?: [],
                    labels: issue.labels ?: [],
                    contributors: issue.contributors ?: [],
                    issueLinks: issue.issueLinks ?: [],
                    links: issue.links ?: [],

                    // History
                    changeHistory: issue.changeHistory ?: [],
                    dailyStatusChanges: issue.dailyStatusChanges ?: [],

                    // Other fields
                    timeTracking: issue.timeTracking,
                    epicLinkField: issue.epicLinkField,
                    rawDescription: issue.rawDescription,
                    dueDate: issue.dueDate,
                    issueUrl: issue.issueUrl,
                    statusCategory: issue.statusCategory,

                    // LLM fields
                    llmTechnicalArea: llmData.llmTechnicalArea ?: '',
                    llmComplexity: llmData.llmComplexity ?: '',
                    llmBusinessImpact: llmData.llmBusinessImpact ?: '',
                    llmRiskLevel: llmData.llmRiskLevel ?: '',
                    llmRiskFactors: llmData.llmRiskFactors ?: []
            ]

            return merged
        }

        println "[MergeClassifiedIssues] Merged ${mergedIssues.size()} issues successfully"
        if (mergedIssues.size() > 0) {
            def first = mergedIssues[0]
            println "[MergeClassifiedIssues] First merged issue: ${first.issueKey} with LLM area: ${first.llmTechnicalArea}"
        }

        projectContext.put("classifiedIssues", mergedIssues)
        return mergedIssues
    }
}