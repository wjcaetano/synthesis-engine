package transforms

import com.capco.brsp.synthesisengine.service.IExecutor
import java.util.regex.Pattern
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class JavaCrawlerClassRelationship implements IExecutor{

    @Override
    Object execute(ApplicationContext applicationContext,
                   Map<String, Object> projectContext,
                   Object... params) {

        Map<String, Object> graph = projectContext?.graph as Map<String, Object>
        if (!graph) return null

        List<Map<String, Object>> nodes = (graph.nodes as Collection<?>)
                ?.collect { it as Map<String, Object> } ?: Collections.emptyList()

        List<Map<String, Object>> withLabels = nodes.findAll { Map n ->
            (n['labels'] as Collection)?.isEmpty() == false
        }

        List<Map<String, Object>> allMethods = withLabels.findAll { Map n ->
            (n['labels'] as Collection).contains('JavaMethod')
        }

        List<Map<String, Object>> allClasses = withLabels.findAll { Map n ->
            (n['labels'] as Collection).contains('Class')
        }

        List<Map<String, Object>> allEnums = withLabels.findAll { Map n ->
            (n['labels'] as Collection).contains('Enum')
        }

        List<Map<String, Object>> missingRelationshipList = allClasses.findAll { Map n ->
            String parentKey = (n['properties'] as Map)?.get('parentKey') as String
            parentKey && !Utils.isEmpty(parentKey)
        }

        missingRelationshipList.addAll(withLabels.findAll { Map n ->
            (n['labels'] as Collection).contains('Call')
        })

        missingRelationshipList.addAll(withLabels.findAll { Map n ->
            String parentKey = (n['properties'] as Map)?.get('parentKey') as String
            (n['labels'] as Collection).contains('Interface') && parentKey && !Utils.isEmpty(parentKey)
        })

        Map<String, String> keyByName = allMethods.collectEntries { Map n ->
            Map props = n['properties'] as Map ?: [:]
            String name = props['name'] as String
            String key  = n['key'] as String
            (name && key) ? [('M:' + name): key] : [:]
        }

        keyByName.putAll(allClasses.collectEntries { Map n ->
            Map props = n['properties'] as Map ?: [:]
            String name = props['name'] as String
            String key  = n['key'] as String
            (name && key) ? [('C:' + name): key] : [:]
        })

        keyByName.putAll(allEnums.collectEntries { Map n ->
            Map props = n['properties'] as Map ?: [:]
            String name = props['name'] as String
            String key  = n['key'] as String
            (name && key) ? [('E:' + name): key] : [:]
        })

        List<Map<String, Object>> newRelations = []

        // Generating InnerClasses Relationship
        missingRelationshipList.each { Map node ->
            Map props = node['properties'] as Map ?: [:]
            String pk = props['parentKey'] as String
            if (!pk) return

            String parentKey
            if ((node['key'] as String).contains('jenum')) {
                parentKey = keyByName['E:' + pk]
            } else if ((node['key'] as String).contains('jfunc')) {
                parentKey = keyByName['M:' + pk]
            } else if ((node['key'] as String).contains('jclass')) {
                parentKey = keyByName['C:' + pk]
            }

            if (!parentKey || Utils.isEmpty(parentKey)) return
            newRelations << [
                    startKey: parentKey,
                    endKey  : node['key'],
                    label   : 'CONTAINS'
            ]
        }

        List rels = (graph['relationships'] as List)
        if (rels == null) {
            rels = []
            graph['relationships'] = rels
        }

        // Remove the direct Relationship between the JavaFile and the MethodCalls
        rels.removeAll {
            def s = it['endKey']?.toString()
            s && s.contains('jfuncall') && !s.contains('jfuncallparam')
        }

        rels.addAll(newRelations)

        return newRelations
    }
}
