import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

class DeduplicateKnowledgeGraph implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        def accumulatedKG = projectContext.accumulatedKnowledgeGraph

        // Deduplicate nodes by id
        def nodesById = [:]
        accumulatedKG.nodes.each { node ->
            if (!nodesById.containsKey(node.id)) {
                nodesById[node.id] = node
            }
        }

        // Deduplicate relationships by source+target+type
        def relationshipsSet = [] as Set
        def uniqueRelationships = []
        accumulatedKG.relationships.each { rel ->
            def key = "${rel.source}|${rel.target}|${rel.type}"
            if (!relationshipsSet.contains(key)) {
                relationshipsSet.add(key)
                uniqueRelationships.add(rel)
            }
        }

        return [
                nodes: nodesById.values() as List,
                relationships: uniqueRelationships
        ]
    }
}
