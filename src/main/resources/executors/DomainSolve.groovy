import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.JavaUtils
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class DomainSolve implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def graphPrograms = projectContext.get("graphPrograms") as List<Map<String, Object>>
        def graphProgramByName = graphPrograms.collectEntries { [(it.name): it] }
        def graphDDMs = projectContext.get("graphDDMs") as List<Map<String, Object>>
        def graphDDMByName = graphDDMs.collectEntries { [(it.name): it] }

        Map<String, Object> domains = projectContext.get("content") as Map<String, Object>

        domains.each { domainName, domain ->
            domain.ddms.each { ddmName, ddm ->
                if (graphDDMByName.containsKey(ddmName)) {
                    domain.ddms[ddmName] = graphDDMByName[ddmName]
                    domain.ddms[ddmName].csharpName = JavaUtils.normalizeJavaIdentifier(ddmName)
                }
            }
            domain.programs.each { programName, program ->
                if (graphDDMByName.containsKey(programName)) {
                    domain.programs[programName] = graphProgramByName[programName]
                    domain.programs[programName].csharpName = JavaUtils.normalizeJavaIdentifier(programName)
                }
            }
        }

        return domains
    }
}