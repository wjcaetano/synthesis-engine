import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.*
import org.springframework.context.ApplicationContext

class AnotherJavaBeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def monolithDecompositionReportString = Utils.decodeBase64ToString(Objects.requireNonNull(projectContext['$api'].files['monolith_decomposition_report.json'], "File 'monolith_decomposition_report.json' is missing!") as String)
        projectContext.put("monolithDecompositionReportString", monolithDecompositionReportString)
        def monolithDecompositionReport = JsonUtils.readAsList(monolithDecompositionReportString)
        monolithDecompositionReport = monolithDecompositionReport.collect {
            [
                name: JavaUtils.normalizeJavaIdentifier(it.cluster_name),
                paragraph: it.paragraph,
                domain: it.domain
            ]
        }

        monolithDecompositionReport.each { usvc ->
            usvc.paragraph.removeAll { usvcParagraph ->
                usvcParagraph.name.contains("EXIT")
            }
            usvc.paragraph.each { usvcParagraph ->
                usvcParagraph.children.removeAll { usvcParagraphChild ->
                    usvcParagraphChild.contains("EXIT")
                }
            }
        }.removeAll { usvc ->
            usvc.paragraph.isEmpty()
        }

        projectContext.put('microserviceBasedProjects', monolithDecompositionReport)

        def reportGrouped = monolithDecompositionReport.groupBy { usvc ->
            usvc.paragraph.size() > 1 || usvc.paragraph.count { usvcParagraph ->
                usvcParagraph.children
            } > 0
        }

        def (reportFinal, reportPieces) = [reportGrouped[true], reportGrouped[false]]

        reportPieces.each { itPieces ->
            itPieces.paragraph.each { itPiecesParagraph ->
                reportFinal.findAll { itFinal ->
                    !itFinal.paragraph.collect { itFinalParagraph -> itFinalParagraph.children.contains(itPiecesParagraph.name) }.isEmpty()
                }.first().paragraph.add(itPiecesParagraph)
            }
        }

        reportFinal = [
                [
                        name        : "monolith",
                        domain      : [],
                        paragraph   : []
                ]
        ]
        monolithDecompositionReport.each { obj ->
            reportFinal[0].domain.addAll(obj.domain)
            reportFinal[0].paragraph.addAll(obj.paragraph)
        }

        reportFinal[0].domain = reportFinal[0].domain.unique()

        def skipped = []
        def sorted = []
        reportFinal[0].paragraph.each { it ->
            def sortedParagraphNames = sorted.collect { it2 -> it2.name }
            if (!it.children.isEmpty() && it.children.any { it3 -> !(it3 in sortedParagraphNames) }) {
                println "Skipping first time '${it.name}' dependency"
                skipped.add(it)
            } else {
                println "The '${it.name}' dependency was already OK"
                sorted.add(it)
            }
        }

        projectContext.put('monolithBasedProjects', reportFinal)

        return "OK"
    }
}