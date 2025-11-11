import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class NewPromptTransform implements ITransform {

    private static final String CONSTANT_USE_LLM_THREAD = "UseLLMThread";
    private static final String CONSTANT_LLM_THREAD_KEY = "LLMThreadKey";

    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        def superService = applicationContext.getBean(SuperService.class)
        def scriptService = applicationContext.getBean(ScriptService.class)
        def tokens = (content =~ /w+[\\p{P}\\p{S}]/).size()
        System.out.println("NewPrompt - Prompt size: ${content.length()} - approx. tokens: ${tokens} - piece: ${content.take(500).replace('\n', '\\n')}${content.length() > 500 ? '...' : ''}")
        def attempts = 0
        def newContent = "FAILED DUE TO LLM PROBLEMS!"
        def exception = null
        def retryProcessed = 0
        def CURRENT_THREAD_CONTEXT = Utils.castOrDefault(projectContext.get("CURRENT_THREAD_CONTEXT"), StringBuilder.class, new StringBuilder());

        while (attempts++ < 3) {
            try {
                newContent = superService.BCF(projectContext, content)
                if (Utils.castOrDefault(projectContext.get(CONSTANT_USE_LLM_THREAD), Boolean.class, Boolean.FALSE)) {
                    CURRENT_THREAD_CONTEXT.append(newContent)
                    CURRENT_THREAD_CONTEXT.append("\n")
                    projectContext.put("CURRENT_THREAD_CONTEXT", CURRENT_THREAD_CONTEXT)
                }
                exception = null
                break
            } catch (Exception exceptionValidation) {
                try {
                    String cause = exceptionValidation.getCause()?.toString()?.toUpperCase()
                    if (cause.contains("TIMEOUT") && retryProcessed <= 3) {
                        if (projectContext.containsKey(CONSTANT_LLM_THREAD_KEY)) {
                            projectContext.remove(CONSTANT_LLM_THREAD_KEY)
                        }

                        final String contextThreadLLM = "@@@openllmthread\n@@@prompt\n" + CURRENT_THREAD_CONTEXT.toString()
                        scriptService.autoEvalStringTransforms(contextThreadLLM)
                        content = CURRENT_THREAD_CONTEXT.toString()
                        retryProcessed++
                        attempts = 0
                    }
                } catch (Exception ex) {
                    exception = ex
                    System.out.println("Exception during the attempt ${attempts} of 3. Waiting 1 minute before retrying!".toString())
                    Thread.sleep(60000)
                }
            }
        }


        if (exception != null) {
            throw exception
        }
        return newContent
    }
}