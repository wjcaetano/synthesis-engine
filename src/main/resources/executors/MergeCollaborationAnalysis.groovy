package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * MergeCollaborationAnalysis.groovy
 *
 * Merges collaboration analysis results from multiple chunks:
 * - Aggregates co-authorship edges by user pairs (sums weights)
 * - Aggregates knowledge silos by area+owner (sums issue counts)
 * - Calculates risk levels based on aggregated counts
 * - Filters silos with less than 5 issues
 */
class MergeCollaborationAnalysis implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def chunks = projectContext.collaborationAnalysisChunks ?: []

        println "[MergeCollaborationAnalysis] Merging ${chunks.size()} collaboration analysis chunks"

        if (chunks.isEmpty()) {
            projectContext.put("collaborationGraph", [edges: [], silos: []])
            return null
        }

        // Aggregate edges by user pair
        def edgesMap = [:]
        def silosMap = [:]

        chunks.each { chunk ->
            // Merge edges (co-authorship)
            chunk.edges?.each { edge ->
                def key = [edge.user1, edge.user2].sort().join('|')
                if (!edgesMap.containsKey(key)) {
                    edgesMap[key] = [
                            user1: edge.user1,
                            user2: edge.user2,
                            weight: 0,
                            type: edge.type ?: 'co-authored'
                    ]
                }
                edgesMap[key].weight += (edge.weight ?: 1)
            }

            // Merge silos (knowledge concentration)
            chunk.silos?.each { silo ->
                def key = "${silo.area}|${silo.owner}"
                if (!silosMap.containsKey(key)) {
                    silosMap[key] = [
                            area: silo.area,
                            owner: silo.owner,
                            issueCount: 0,
                            riskLevel: 'Low'
                    ]
                }
                silosMap[key].issueCount += (silo.issueCount ?: 1)
            }
        }

        // Calculate risk levels for silos based on issue count
        silosMap.values().each { silo ->
            if (silo.issueCount >= 20) {
                silo.riskLevel = 'High'
            } else if (silo.issueCount >= 10) {
                silo.riskLevel = 'Medium'
            } else {
                silo.riskLevel = 'Low'
            }
        }

        def collaborationGraph = [
                edges: edgesMap.values() as List,
                silos: silosMap.values().findAll { it.issueCount >= 5 } as List  // Only silos with 5+ issues
        ]

        println "[MergeCollaborationAnalysis] Merged results: ${collaborationGraph.edges.size()} edges, ${collaborationGraph.silos.size()} silos"

        projectContext.put("collaborationGraph", collaborationGraph)
    }
}
