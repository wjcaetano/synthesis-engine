import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.JavaUtils
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class CsharpBeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def monolithDecompositionReportString = Utils.decodeBase64ToString(Objects.requireNonNull(projectContext['$api'].files['monolith_decomposition_report.json'], "File 'monolith_decomposition_report.json' is missing!") as String)
        projectContext.put("monolithDecompositionReportString", monolithDecompositionReportString)
        def monolithDecompositionReport = JsonUtils.readAsList(monolithDecompositionReportString)
        println("monolith")
        monolithDecompositionReport = monolithDecompositionReport.collect {
            [
                    name     : JavaUtils.normalizeJavaIdentifier(it.cluster_name),
                    paragraph: it.paragraph,
                    domain   : it.domain
            ]
        }

        projectContext.put('microserviceBasedProjects', monolithDecompositionReport)

        def reportFinal = [
                [
                        name     : "monolith",
                        domain   : [],
                        paragraph: []
                ]
        ]
        monolithDecompositionReport.each { obj ->
            reportFinal[0].domain.addAll(obj.domain)
            reportFinal[0].paragraph.addAll(obj.paragraph)
        }

        reportFinal[0].domain = reportFinal[0].domain.unique()

        projectContext.put('monolithBasedProjects', Utils.convertToConcurrent(reportFinal))

        return "OK"
    }
}