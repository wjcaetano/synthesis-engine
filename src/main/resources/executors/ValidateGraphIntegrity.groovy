package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext
import com.capco.brsp.synthesisengine.utils.*

class ValidateGraphIntegrity implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        def kg = projectContext.unifiedKnowledgeGraph

        if (kg == null) {
            return [
                    valid: false,
                    errors: ["No knowledge graph found in context"],
                    warnings: [],
                    stats: [:]
            ]
        }

        def nodes = kg.nodes ?: []
        def relationships = kg.relationships ?: []
        def errors = []
        def warnings = []

        // 1. Check for nodes without ID
        def nodesWithoutId = nodes.findAll { !it.id }
        if (!nodesWithoutId.isEmpty()) {
            errors.add("Found ${nodesWithoutId.size()} nodes without ID")
        }

        // 2. Check for nodes without labels
        def nodesWithoutLabels = nodes.findAll { !it.labels || it.labels.isEmpty() }
        if (!nodesWithoutLabels.isEmpty()) {
            warnings.add("Found ${nodesWithoutLabels.size()} nodes without labels")
        }

        // 3. Check for orphan relationships (source or target not in nodes)
        def nodeIds = nodes.collect { it.id } as Set
        def orphanRelationships = relationships.findAll { rel ->
            !nodeIds.contains(rel.source) || !nodeIds.contains(rel.target)
        }
        if (!orphanRelationships.isEmpty()) {
            errors.add("Found ${orphanRelationships.size()} orphan relationships (missing source/target nodes)")
        }

        // 4. Check for relationships without type
        def relationshipsWithoutType = relationships.findAll { !it.type }
        if (!relationshipsWithoutType.isEmpty()) {
            errors.add("Found ${relationshipsWithoutType.size()} relationships without type")
        }

        // 5. Check for duplicate node IDs
        def nodeIdCounts = [:]
        nodes.each { node ->
            def id = node.id
            nodeIdCounts[id] = (nodeIdCounts[id] ?: 0) + 1
        }
        def duplicateIds = nodeIdCounts.findAll { it.value > 1 }
        if (!duplicateIds.isEmpty()) {
            warnings.add("Found ${duplicateIds.size()} duplicate node IDs: ${duplicateIds.keySet().take(5)}")
        }

        // 6. Count by label
        def labelCounts = [:]
        nodes.each { node ->
            node.labels?.each { label ->
                labelCounts[label] = (labelCounts[label] ?: 0) + 1
            }
        }

        // 7. Count by relationship type
        def relTypeCounts = [:]
        relationships.each { rel ->
            def type = rel.type
            relTypeCounts[type] = (relTypeCounts[type] ?: 0) + 1
        }

        def stats = [
                totalNodes: nodes.size(),
                totalRelationships: relationships.size(),
                uniqueLabels: labelCounts.size(),
                uniqueRelationshipTypes: relTypeCounts.size(),
                labelCounts: labelCounts,
                relationshipTypeCounts: relTypeCounts,
                nodesWithoutId: nodesWithoutId.size(),
                nodesWithoutLabels: nodesWithoutLabels.size(),
                orphanRelationships: orphanRelationships.size(),
                relationshipsWithoutType: relationshipsWithoutType.size(),
                duplicateNodeIds: duplicateIds.size()
        ]

        def graphValidation = [
                valid: errors.isEmpty(),
                errors: errors,
                warnings: warnings,
                stats: stats
        ]
        projectContext.put("graphValidation", graphValidation)
        return graphValidation
    }
}