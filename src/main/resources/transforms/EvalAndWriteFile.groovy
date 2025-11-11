import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils

class EvalAndWriteFileTransform implements ITransform {
    Object anyCollectionGet(Object target, String path) {
        try {
            return JsonPath.read(target, path)
        } catch (PathNotFoundException pnfe) {
            return null
        }
    }

    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        def scriptService = applicationContext.getBean(ScriptService.class)
        def params = Utils.splitParams(transformParams)
        def templateReference = scriptService.autoEval(params.removeFirst() as String)
        def evaluatedPath = scriptService.autoEval(params.removeFirst() as String)

        def relativeFilePath = FileUtils.pathJoin(projectContext.project.name, evaluatedPath).toString().trim()
        def fullFilePath = FileUtils.absolutePathJoin(projectContext.rootFolder, relativeFilePath)

        def filesMetaData = projectContext.files_metadata
        if (filesMetaData == null) {
            filesMetaData = new ConcurrentLinkedHashMap<>()
            projectContext.files_metadata = filesMetaData
        }

        def tempFile = filesMetaData.get(relativeFilePath)
        if (tempFile == null) {
            tempFile = new ConcurrentLinkedHashMap<>()
            filesMetaData.put(relativeFilePath, tempFile)
        }

        def tempHistory = tempFile.history
        if (tempHistory == null) {
            tempHistory = new ConcurrentLinkedList<String>()
            tempFile.history = tempHistory
        }

        String templateContent = anyCollectionGet(projectContext, templateReference) as String
        String evaluatedContent = scriptService.autoEval(templateContent, tempHistory)

        SuperUtils.WGB(fullFilePath, evaluatedContent, false)

        return content
    }
}