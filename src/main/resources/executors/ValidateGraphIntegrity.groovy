package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

class ValidateGraphIntegrity implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        def unifiedKG = projectContext.unifiedKnowledgeGraph

        if (unifiedKG == null) {
            return [
                    valid: false,
                    errors: ["No unifiedKnowledgeGraph found in context"],
                    warnings: [],
                    stats: [totalNodes: 0, totalRelationships: 0]
            ]
        }

        def nodes = unifiedKG.nodes ?: []
        def relationships = unifiedKG.relationships ?: []
        def errors = []
        def warnings = []

        // Count node labels
        def labelCounts = [:]
        def uniqueLabels = [] as Set
        nodes.each { node ->
            node.labels?.each { label ->
                if (label != null) {
                    labelCounts[label] = (labelCounts[label] ?: 0) + 1
                    uniqueLabels.add(label)
                }
            }
        }

        // Count relationship types (filter nulls!)
        def relationshipTypeCounts = [:]
        def uniqueRelTypes = [] as Set
        relationships.each { rel ->
            def type = rel.label ?: rel.type
            if (type != null && type != 'null') {  // Filter null keys
                relationshipTypeCounts[type] = (relationshipTypeCounts[type] ?: 0) + 1
                uniqueRelTypes.add(type)
            }
        }

        // Validate nodes
        def nodesWithoutId = nodes.findAll { !it.id }.size()
        def nodesWithoutLabels = nodes.findAll { !it.labels || it.labels.isEmpty() }.size()

        if (nodesWithoutId > 0) {
            errors.add("Found ${nodesWithoutId} nodes without 'id' field")
        }
        if (nodesWithoutLabels > 0) {
            warnings.add("Found ${nodesWithoutLabels} nodes without labels")
        }

        // Validate relationships
        def relsWithoutType = relationships.findAll { !it.label && !it.type }.size()
        if (relsWithoutType > 0) {
            warnings.add("Found ${relsWithoutType} relationships without label/type")
        }

        // Check for orphan relationships
        def nodeIds = nodes.collect { it.id }.findAll { it != null } as Set
        def orphanRels = relationships.findAll { rel ->
            !nodeIds.contains(rel.startKey) || !nodeIds.contains(rel.endKey)
        }.size()

        if (orphanRels > 0) {
            warnings.add("Found ${orphanRels} orphan relationships (missing nodes)")
        }

        def stats = [
                totalNodes: nodes.size(),
                totalRelationships: relationships.size(),
                uniqueLabels: uniqueLabels.size(),
                uniqueRelationshipTypes: uniqueRelTypes.size(),
                labelCounts: labelCounts,
                relationshipTypeCounts: relationshipTypeCounts,
                nodesWithoutId: nodesWithoutId,
                nodesWithoutLabels: nodesWithoutLabels,
                orphanRelationships: orphanRels,
                relationshipsWithoutType: relsWithoutType,
                duplicateNodeIds: 0
        ]

        def validation = [
                valid: errors.isEmpty(),
                errors: errors,
                warnings: warnings,
                stats: stats
        ]

        projectContext.put("graphValidation", validation)
        return validation
    }
}