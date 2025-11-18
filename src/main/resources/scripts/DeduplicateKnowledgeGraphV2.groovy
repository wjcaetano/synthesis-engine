import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext
import com.capco.brsp.synthesisengine.utils.*

class DeduplicateKnowledgeGraphV2 implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        def unifiedKG = projectContext.unifiedKnowledgeGraph

        if (unifiedKG == null) {
            return [
                nodes: [],
                relationships: [],
                stats: [
                    nodesRemoved: 0,
                    relationshipsRemoved: 0,
                    error: "No unifiedKnowledgeGraph found in context"
                ]
            ]
        }

        def nodes = unifiedKG.nodes ?: []
        def relationships = unifiedKG.relationships ?: []

        // 1. Deduplicate nodes by id (keep first occurrence, merge properties)
        def nodesById = [:]
        nodes.each { node ->
            def nodeId = node.id
            if (!nodesById.containsKey(nodeId)) {
                nodesById[nodeId] = node
            } else {
                // Merge properties from duplicates (non-null values take precedence)
                def existing = nodesById[nodeId]
                node.properties?.each { key, value ->
                    if (value != null && existing.properties[key] == null) {
                        existing.properties[key] = value
                    }
                }
            }
        }

        // 2. Deduplicate relationships by source+target+type
        def relationshipsSet = [] as Set
        def uniqueRelationships = []
        relationships.each { rel ->
            def key = "${rel.source}|${rel.target}|${rel.type}"
            if (!relationshipsSet.contains(key)) {
                relationshipsSet.add(key)
                uniqueRelationships.add(rel)
            }
        }

        // 3. Validate referential integrity (remove relationships with missing nodes)
        def nodeIds = nodesById.keySet()
        def validRelationships = uniqueRelationships.findAll { rel ->
            nodeIds.contains(rel.source) && nodeIds.contains(rel.target)
        }

        def stats = [
            originalNodes: nodes.size(),
            originalRelationships: relationships.size(),
            nodesRemoved: nodes.size() - nodesById.size(),
            relationshipsRemoved: relationships.size() - validRelationships.size(),
            finalNodes: nodesById.size(),
            finalRelationships: validRelationships.size()
        ]

        return [
            nodes: nodesById.values() as List,
            relationships: validRelationships,
            stats: stats
        ]
    }
}
