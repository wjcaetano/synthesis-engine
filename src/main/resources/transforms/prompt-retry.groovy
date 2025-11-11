import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.service.ScriptService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext

class PromptRetry implements ITransform {
    def logger = LoggerFactory.getLogger("PromptRetry")

    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        ScriptService scriptService = applicationContext.getBean(ScriptService.class)
        def attempts = 0
        while (attempts++ < 3) {
            try {
                logger.info("PromptRetry attempt ${attempts}".toString())
                var answer = scriptService.handleAgent(projectContext, content)
                return answer
            } catch (Exception ex) {
                logger.error("Attempt ${attempts} failed to execute the prompt.\nError message: ${ex.getMessage()}".toString())
            }
        }

        return "Failed to execute process the content below:\n\n${content}"
    }
}
