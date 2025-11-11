import com.capco.brsp.synthesisengine.dto.TransformDto
import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.Utils
import com.capco.brsp.synthesisengine.utils.JavaUtils
import org.springframework.context.ApplicationContext

class RepromptAllMethods2Transform implements ITransform {
    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        def scriptService = applicationContext.getBean(ScriptService.class)
        def extractedMarkdown = (String) Utils.extractMarkdownCode(content)
        def listAllMethods = JavaUtils.listAllMethods(extractedMarkdown)
        def newContent = content


        def relativeFilePath = FileUtils.pathJoin(projectContext.cluster.clusterNormalizedName, projectContext.filePath as String)
        def filesMetadata = projectContext.get("files_metadata") as ConcurrentLinkedHashMap<String, Object>
        def fileHistory = filesMetadata.get(relativeFilePath.toString()).history as List<TransformDto>

        int i = 1
        System.out.println("Start Reprompting All Methods!")
        for (String method : listAllMethods) {
            System.out.println("Reprompting method ${i} of ${listAllMethods.size()} named as '${method}'".toString())
            String repromptText = """
                          First of all, check if the [ORIGINAL METHOD CONTENT] seems to be inmcomplete. If so, do the [TASK] below, otherwise just return a OK message!
                          
                          [TASK]
                          1. Improve the current method to close any gaps considering the rest of the class and the original COBOL code you received before.
                          2. Most of the current gaps are already mentioned within the comments.
                          3. Create just one METHOD with the same signature as the original to replace it!
                          4. Do your best to fix the gaps on this method turning the translated code on a full implementation of the original COBOL paragraph.
                          5. Don't include anything else to your answer instead of the code method to replace the original one.
                          6. You aren't allowed to create more methods, neither classes, your job is just to complete the code of the current method.
      
                          [ORIGINAL METHOD CONTENT]
                          ${method}
                        """.toString()

            scriptService.addHistory(fileHistory, "Reprompting Start: " + method, repromptText)

            def attempts = 0
            def newMethodContent = method
            def exception = null
            while (attempts++ < 3) {
                try {
                    newMethodContent = scriptService.handleAgent(projectContext, repromptText)
                    newMethodContent = (String) Utils.extractMarkdownCode(newMethodContent)
                    scriptService.addHistory(fileHistory, "Reprompting End: " + method, repromptText)
                    if (newMethodContent != "OK") {
                        newContent = newContent.replace(method, newMethodContent)
                    }
                    exception = null
                    break
                } catch (Exception ex) {
                    exception = ex
                    System.out.println("Exception during the attempt ${attempts} of 3. Waiting 1 minute before retrying!".toString())
                    Thread.sleep(60000)
                }
            }

            if (exception != null) {
                throw exception
            }
            i++
        }
        System.out.println("Finished Reprompting All Methods!")

        return newContent
    }
}