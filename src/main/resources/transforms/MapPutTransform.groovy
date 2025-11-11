import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import org.springframework.context.ApplicationContext
import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.utils.Utils

class MapPutTransform implements ITransform {
    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        try {
            def scriptService = applicationContext.getBean(ScriptService.class)

            var mapPath = transformParams.split(",")[0].trim()
            var mapKey = transformParams.split(",")[1].trim()

            mapKey = scriptService.evalFreemarker(mapKey)
            println "Setting '${mapKey}' in map '${mapPath}' with: '${content.take(500).replace('\n', '\\n')}${content.length() > 500 ? '...' : ''}'"
            var map = Utils.anyCollectionGetOrSet(projectContext, mapPath, new ConcurrentLinkedHashMap<String, String>())
            map.put(mapKey, content)
        } catch (Exception ex) {
            ex.printStackTrace()
        }
        return content
    }
}