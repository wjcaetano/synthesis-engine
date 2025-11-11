import com.capco.brsp.synthesisengine.dto.TransformDto
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.JsonUtils
import org.springframework.context.ApplicationContext
import org.springframework.web.client.HttpServerErrorException
import java.nio.file.Path

class BeforeEachMicroservice implements IExecutor {
    Object eval(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, Path relativePath) {
        def scriptService = applicationContext.getBean(ScriptService.class)
        def attempts = 0
        def result = null
        def recipe = projectContext.recipe
        while (attempts++ < 3) {
            try {
                List<TransformDto> history = new ConcurrentLinkedList<>()
                def filePath = FileUtils.absolutePathJoin(projectContext.rootFolder, relativePath)
                if (false && recipe.cacheMap?.containsKey(filePath.toString())) {
                    System.out.println("CACHE - Retrieving from cache: ${filePath.toString()}".toString())
                    result = recipe.cacheMap.get(filePath.toString())
                    System.out.println("CACHE - Retrieving from cache: ${filePath.toString()}".toString())
                } else {
                    result = scriptService.autoEval(content, history) as String
                }
                result = scriptService.autoEval(content, []) as String

                FileUtils.writeFile(filePath, result, false)
                projectContext.putIfAbsent("files_metadata", new ConcurrentLinkedHashMap<>())
                def filesMetadata = projectContext.get("files_metadata") as ConcurrentLinkedHashMap<String, Object>
                filesMetadata.put(relativePath.toString(), [history: history])

                break
            } catch (HttpServerErrorException ex) {
                System.out.println("HttpServerErrorException during the attempt ${attempts} of 3. Waiting 1 minute before retrying!".toString())
                Thread.sleep(60000)
            }
        }

        return result
    }

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        if (projectContext.cluster == null) {
            projectContext.cluster = new ConcurrentLinkedHashMap<>()
        }
        def cluster = projectContext.cluster as Map<String, Object>
        def prompts = projectContext.recipe.prompts as Map<String, Object>

        System.out.println("Creating jclProfile")
        def jclProfileCache = FileUtils.pathJoin(cluster.clusterNormalizedName, 'cache', 'jclProfile.json')
        def jclProfileString = eval(applicationContext, projectContext, prompts.jclSummarization as String, jclProfileCache)
        def jclProfile = JsonUtils.readAsMap(jclProfileString)
        cluster.jclProfile = jclProfile
        System.out.println("Completed jclProfile")
    }
}