import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import org.springframework.context.ApplicationContext

class ReactBeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def templates = projectContext.recipe.templates as Map<String, Object>
        def screenSummaryCypherQuery = templates.screenSummaryCypherQuery as String
        def result = scriptService.autoEval(screenSummaryCypherQuery) as String
        projectContext.cicsScreens = JsonUtils.readAsList(result)
    }
}