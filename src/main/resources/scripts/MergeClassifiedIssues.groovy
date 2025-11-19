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
        def mergedIssues = normalizedIssues.collect { issue ->
            def issueKey = issue.issueKey
            def llmData = llmMap[issueKey]

            if (llmData) {
                // Merge: create new map with all original fields plus LLM fields
                def merged = new HashMap(issue)
                merged.llmTechnicalArea = llmData.llmTechnicalArea ?: ''
                merged.llmComplexity = llmData.llmComplexity ?: ''
                merged.llmBusinessImpact = llmData.llmBusinessImpact ?: ''
                merged.llmRiskLevel = llmData.llmRiskLevel ?: ''
                merged.llmRiskFactors = llmData.llmRiskFactors ?: []
                return merged
            } else {
                // No LLM data for this issue, use as-is with empty LLM fields
                def merged = new HashMap(issue)
                merged.llmTechnicalArea = ''
                merged.llmComplexity = ''
                merged.llmBusinessImpact = ''
                merged.llmRiskLevel = ''
                merged.llmRiskFactors = []
                return merged
            }
        }

        println "[MergeClassifiedIssues] Merged ${mergedIssues.size()} issues"
        if (mergedIssues.size() > 0) {
            println "[MergeClassifiedIssues] First merged issue keys: ${mergedIssues[0].keySet()}"
        }

        projectContext.put("classifiedIssues", mergedIssues)
        return null
    }
}
