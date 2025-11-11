import com.capco.brsp.synthesisengine.dto.TransformDto
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.tools.ToolsFunction
import com.capco.brsp.synthesisengine.utils.*
import org.springframework.context.ApplicationContext
import org.springframework.web.client.HttpServerErrorException

import java.nio.file.Path

class BeforeAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def tempCobolPrograms = Utils.decodeBase64ToString(Objects.requireNonNull(projectContext['$api'].files['legacy_code.json'], "File 'legacy_code.json' is missing!") as String)
        def tempOriginalProjectsContent = Utils.decodeBase64ToString(Objects.requireNonNull(projectContext['$api'].files['monolith_decomposition_report.json'], "File 'monolith_decomposition_report.json' is missing!") as String)
        def tempTables = projectContext['$api'].files.tables
        def tables = tempTables instanceof List ? tempTables : JsonUtils.readAsList(tempTables)
        def tempCobol = (tempCobolPrograms instanceof Map ? tempCobolPrograms : JsonUtils.readAsMap(tempCobolPrograms))
        def cobol = tempCobol.cobolPrograms
        def monolithDecompositionReport = tempOriginalProjectsContent instanceof List ? tempOriginalProjectsContent : JsonUtils.readAsList(tempOriginalProjectsContent)

        projectContext['$api'].files.cobolPrograms = tempCobol
        projectContext['$api'].files.clusters = monolithDecompositionReport

        if (projectContext.files == null) {
            projectContext.files = new ConcurrentLinkedHashMap<>()
        }
        projectContext.files.cobolPrograms = tempCobol

        def output = []
        def port = projectContext.recipe?.vars?.port ?: 8080

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
                        project_name: "monolith",
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

        println "Now fixing ${skipped.size()} dependencies"
        def i = 0
        while (!skipped.isEmpty()) {
            if (i > skipped.size()) {
                println "Unable to solve a sort of '${skipped.size()}' circular dependencies"
                break
            }
            def skippedItem = skipped.remove(0)
            def sortedParagraphNames = sorted.collect { it2 -> it2.name }
            if (!skippedItem.children.isEmpty() && skippedItem.children.any { it3 -> !(it3 in sortedParagraphNames) }) {
                println "Skipping again '${skippedItem.name}' depedency"
                skipped.add(skippedItem)
                i++
            } else {
                println "Fixed '${skippedItem.name}' dependency"
                sorted.add(skippedItem)
                i = 0
            }
        }

        while (!skipped.isEmpty()) {
            def removed = skipped.remove(0)
            println "Including the unsolvable item named '${removed.name}'"
            sorted.add(removed)
        }

        reportFinal[0].paragraph = sorted

        monolithDecompositionReport = reportFinal

        projectContext['$api'].files.projects = Utils.convertToConcurrent(monolithDecompositionReport)

        monolithDecompositionReport.eachWithIndex { project, index ->
            def programs = project.paragraph
                    .collect { paragraph -> ((String) paragraph.name).split('\\.')[0] }
                    .unique()
            def transformedProject = [
                    name          : project.project_name,
                    normalizedName: project.project_name.replaceAll("\\s+-\\s+|\\s", "_").toLowerCase(),
                    port          : port + index,
                    dtos          : project.domain
                            .findAll { it.startsWith("call-variable:") }
                            .collect { variable ->
                                def storageName = variable.replaceFirst("^.*", "")
                                def storage = cobol.findAll { programs.contains(it.name) }
                                        .collect { cob ->
                                            [
                                                    program: cob.name,
                                                    code   : cob.working_storage.data_entries.find { d -> d.name == storageName }?.raw_code
                                            ]
                                        }
                                        .find { it != null && it.code != null } ?: [program: '<NOT_FOUND>', raw_code: '<NOT_FOUND>']
                                return [program: storage.program, name: storageName, code: storage.code, file: storageName.findAll(/[a-zA-Z0-9]+/).collect { dto -> dto.toLowerCase().capitalize() }.join('') + 'Dto.java']
                            },
                    files         : project.domain
                            .findAll { it.startsWith("file-") }
                            .collect { variable ->
                                def storageName = variable.replaceFirst("^.*:", "")
                                def storage = cobol.findAll { programs.contains(it.name) }
                                        .collect { cob ->
                                            [
                                                    program: cob.name,
                                                    code   : cob.file_section?.data_entries?.find { d -> d.name == storageName }?.raw_code
                                            ]
                                        }
                                        .find { it != null && it.code != null } ?: [program: '<NOT_FOUND>', raw_code: "<NOT_FOUND>"]
                                return [program: storage.program, name: storageName, code: storage.code, file: storageName.findAll(/[a-zA-Z0-9]+/).collect { dto -> dto.toLowerCase().capitalize() }.join('') + 'Model.java']
                            },
                    tables        : project.domain
                            .findAll { it.startsWith("table-") }
                            .collect { variable ->
                                def tableName = variable.replaceFirst("^.*:", "")
                                def table = tables.find { t -> tableName.equalsIgnoreCase(t.table.name) }?.table
                                return [program: "<NOT_FOUND>", name: table?.name, code: table?.raw_code, file: tableName.findAll(/[a-zA-Z0-9]+/).collect { dto -> dto.toLowerCase().capitalize() }.join('') + 'Model.java']
                            },
                    locals        : project.domain
                            .findAll { !it.startsWith("call-variable") && !it.startsWith("file-") && !it.startsWith("table-") && !it.startsWith("cursor-") }
                            .collect { variable ->
                                def storageName = variable.replaceFirst("^.*:", "")
                                def storage = cobol.findAll { programs.contains(it.name) }
                                        .collect { cob ->
                                            [
                                                    program: cob.name,
                                                    code   : cob.working_storage.data_entries.find { d -> d.name == storageName }?.raw_code
                                            ]
                                        }
                                        .find { it != null && it.code != null } ?: [program: '<NOT_FOUND>', raw_code: "<NOT_FOUND>"]
                                return [program: storage.program, name: storageName, code: storage.code, file: storageName.findAll(/[a-zA-Z0-9]+/).collect { dto -> dto.toLowerCase().capitalize() }.join('') + 'Dto.java']
                            },
                    paragraphs    : project.paragraph.collect { para ->
                        def parts = para.name.split('\\.')
                        def calls = para.children.collect { child ->
                            def childParts = child.split('\\.')
                            def microservice = monolithDecompositionReport.find { it.paragraph.any { p -> p.name == child } }?.project_name
                            if (microservice == project.project_name) {
                                return null
                            }
                            return [
                                    microservice: microservice,
                                    program     : childParts[0],
                                    paragraph   : childParts[1],
                                    code        : cobol.findAll { it.name == childParts[0] }.collect { source ->
                                        source.procedure_division.paragraphs.find { p -> p.name == childParts[1] }?.raw_code
                                    }.find { it != null } ?: '<NOT_FOUND>',
                                    endpoint    : "/${childParts[0].toLowerCase()}-${childParts[1].toLowerCase()}".toString()
                            ]
                        }.findAll { it != null }
                        [
                                cobol_path  : para.name,
                                microservice: "monolith",
                                program     : parts[0],
                                name        : parts[1],
                                code        : cobol.findAll { it.name == parts[0] }.collect { source ->
                                    source.procedure_division.paragraphs.find { p -> p.name == parts[1] }?.raw_code
                                }.find { it != null } ?: '<NOT_FOUND>',
                                calls       : calls
                        ]
                    }
            ]
            def clientsMap = [:]
            transformedProject.paragraphs.each { p ->
                p.calls.each { entry ->
                    if (entry.microservice) {
                        def microserviceClientName = entry.microservice.findAll(/[a-zA-Z0-9]+/).collect { dto -> dto.toLowerCase().capitalize() }.join('') + 'Client.java'
                        if (!clientsMap.containsKey(microserviceClientName)) {
                            clientsMap[microserviceClientName] = [(entry.endpoint): entry.code]
                        } else {
                            def microserviceClientMap = clientsMap[microserviceClientName]
                            if (!microserviceClientMap.containsKey(entry.endpoint)) {
                                microserviceClientMap[entry.endpoint] = entry.code
                            }
                        }
                    }
                }
            }
            transformedProject.clients = clientsMap
            output << transformedProject
        }

        projectContext.put('blueprint', output)
        def blueprintCache = FileUtils.absolutePathJoin(projectContext.rootFolder, 'cache', 'blueprintContext.json')
        FileUtils.writeFile(blueprintCache, JsonUtils.writeAsJsonString(output, true), false)

        def recipe = projectContext.recipe as Map<String, Object>
        def vars = recipe.vars as Map<String, Object>

        List<Map<String, Object>> projects = new ConcurrentLinkedList<>()
        projectContext.put("projects", projects)
        monolithDecompositionReport.eachWithIndex { def report, int reportIndex ->
            def programsNames = report.paragraph.collect { paragraph -> ((String) paragraph.name).split('\\.')[0] }.unique()
            def paragraphNames = report.paragraph.collect { paragraph -> ((String) paragraph.name).split('\\.')[1] }.unique()
            println "Paragraph Names: ${paragraphNames.join(', ')}"
            def matchedCobolPrograms = cobol.findAll(cob -> programsNames.contains(cob.name)) as List<Map<String, Object>>

            Map<String, Object> project = new ConcurrentLinkedHashMap<>()
            projects.add(project)
            projectContext.put('project', project)

            String projectName = report.project_name
            project.put("projectName", projectName)
            project.put("projectNormalizedName", projectName.replaceAll("\\s+-\\s+|\\s", "_").toLowerCase())
            project.put('programsNames', Utils.convertToConcurrent(programsNames))
            project.put('isBatch', true)
            project.put('index', reportIndex)

            def categorizedAndCollectNames = { domain, prefixes ->
                domain.findAll { entry ->
                    prefixes.any { prefix -> entry.matches(prefix + ".*:.*") }
                }.collect { entry ->
                    def matchingPrefix = prefixes.find { prefix -> entry.matches(prefix + ".*:.*") }
                    return entry.replaceFirst("^.*${matchingPrefix}.*:", "").replaceFirst("\\.\$", "")
                }
            }

            def getStorageEntry = { section, storageName, sourcePrograms ->
                sourcePrograms.collect { prog ->
                    [program: prog.name, raw_code: prog[section]?.data_entries?.find { d -> d.name == storageName }?.raw_code]
                }.find { it != null && it.raw_code != null } ?: [program: "<NOT_FOUND>", raw_code: "<NOT_FOUND>"]
            }

            def generateStructure = { section, prefixList, fileSuffix ->
                def names = categorizedAndCollectNames(report.domain, prefixList)
                names.collect { storageName ->
                    def storage = getStorageEntry(section, storageName, matchedCobolPrograms)
                    [program: storage.program, name: storageName, code: storage.raw_code, file: JavaUtils.normalizeJavaIdentifier(storageName) + fileSuffix]
                }
            }

            def generateTableModel = { domains, prefixList, fileSuffix ->
                def names = categorizedAndCollectNames(domains, prefixList)
                names.collect { tableName ->
                    def tableItem = tempCobol.db2Tables.find { tableItem -> tableName.matches("(.+\\.)?" + tableItem.name) }
                    [program: "<DOESNT_MATTER>", name: tableItem?.name, code: tableItem?.rawCode ?: tableItem?.raw_code, file: JavaUtils.normalizeJavaIdentifier(tableItem?.name ?: "") + fileSuffix]
                }.findAll { it != null && it.code != null } ?: []
            }

            def cypherJolt = { String cypherQuery, String joltSpec ->
                def toolsFunction = applicationContext.getBean(ToolsFunction.class)

                def url = vars.neo4jURL as String
                def method = "POST"
                def headers = [
                        "Authorization": vars.neo4jEncodedAuth,
                        "Content-Type" : "application/json"
                ] as Map<String, String>

                def body = [
                        statements: [
                                [statement: cypherQuery]
                        ]
                ]

                def neo4jResult = toolsFunction.apiCall(url, method, body, headers)
                def neo4jResultString = JsonUtils.writeAsJsonString(neo4jResult, true)

                def neo4jResultSize = (neo4jResult["results"]["data"][0]).size()
                if (neo4jResultSize == 0){
                    return null
                }

                def joltResult = toolsFunction.jolt(neo4jResultString, joltSpec)

                return JsonUtils.readAsList(joltResult)
            }

            def prompts = projectContext.recipe.prompts as Map<String, String>
            boolean pending = true
            while (pending) {
                try {
                    def sqlSummaryCache = FileUtils.pathJoin(project.projectNormalizedName, 'cache', 'sqlSummary.json')
                    def sqlSummarizationString = summaryEvaluation(projectContext, prompts.sqlSummarization, sqlSummaryCache)
                    sqlSummarizationString = (Utils.extractMarkdownCode(sqlSummarizationString) as String).trim().replaceAll(/^["'`"]+|["'`"]+$/, '')
                    def sqlSummary = JsonUtils.readAsList(sqlSummarizationString)
                    sqlSummary.collect {
                        [
                                "tableName"                            : it.tableName.replace("^.*\\.", ""),
                                "originalQuery"                        : it.originalQuery,
                                "springQueryAnnnotatedRepositoryMethod": it.springQueryAnnnotatedRepositoryMethod
                        ]
                    }
                    project.put('sqlSummary', Utils.convertToConcurrent(sqlSummary))
                    println "SQL Summary: ${sqlSummary}"
                    pending = false
                } catch (Exception ex) {
                    println "Failed to generate a SQL Summary: ${ex.getMessage()}"
                    project.put('sqlSummary', Utils.convertToConcurrent([]))
                }
            }

            String serviceSummarizationString = ""
            pending = true
            while (pending) {
                try {
                    def serviceSummaryCache = FileUtils.pathJoin(project.projectNormalizedName, 'cache', 'serviceSummary.json')
                    serviceSummarizationString = summaryEvaluation(projectContext, prompts.serviceSummarizationSimple, serviceSummaryCache)
                    def serviceSummary = JsonUtils.readAsList(serviceSummarizationString)
                    project.put('serviceSummary', Utils.convertToConcurrent(serviceSummary))
                    println "Service Summary: ${serviceSummary}"
                    pending = false
                } catch (Exception ex) {
                    println "Failed to generate a Service Summary: ${ex.getMessage()}"
                    project.put('serviceSummary', Utils.convertToConcurrent([]))
                }
            }

            println "Creating jclProfile"
            def jclProfileCache = FileUtils.pathJoin(project.projectNormalizedName, 'cache', 'jclProfile.json')
            def jclProfileString = summaryEvaluation(projectContext, prompts.jclSummarization, jclProfileCache) as String
            def jclProfile = JsonUtils.readAsMap(jclProfileString)
            project.jclProfile = jclProfile
            println "Completed jclProfile"

            def summarization = recipe.summarization as Map<String, Object>
            def classifiedCobolVariables = cypherJolt(summarization.cobolVariablesClassificationCypherQuery as String, summarization.joltNeo4jTableToJson as String)

            def classifiedCobolVariablesGroup = classifiedCobolVariables.groupBy { it.label.contains("COBOLLinkage") }
            def dtos = classifiedCobolVariablesGroup.get(true)
            def locals = classifiedCobolVariablesGroup.get(false)

            def cobolFullVariables = cypherJolt(summarization.cobolFullVariablesCypherQuery as String, summarization.joltNeo4jTableToJson as String)
            project.put('cobolFullVariables', Utils.convertToConcurrent(cobolFullVariables))

            def cobolVariables = cypherJolt(summarization.cobolVariablesCypherQuery as String, summarization.joltNeo4jTableToJson as String)
            project.put('cobolVariables', Utils.convertToConcurrent(cobolVariables))

//            def fileVariables = cypherJolt(summarization.fileVariablesCypherQuery as String, summarization.joltNeo4jTableToJson as String)
//            project.put('fileVariables', Utils.convertToConcurrent(fileVariables))

            def fileComprehensiveDetails = cypherJolt(summarization.fileComprehensiveDetailsCypherQuery as String, summarization.joltNeo4jTableToJson as String)
            project.put('fileComprehensiveDetails', Utils.convertToConcurrent(fileComprehensiveDetails))

            def domains = fileComprehensiveDetails.collect { it ->
                return [
                        program: it.program,
                        name   : it.domainJavaName,
                        code   : it.rawCode,
                        file   : it.domainJavaName + ".java",
                        label  : it.label,
                        fields : it.recordStorageLowestLevel,
                        pks    : it.recordKeyLowestLevel
                ]
            }.groupBy { item -> item.label == "COBOLFileControl" }

            def pojos = domains.get(true)
            def models = domains.get(false)

//            def models = generateTableModel(report.domain, ["table", "cursor"], ".java")?.unique()
//            fileVariables.findAll { it -> it.label == "COBOLVsamFile" }.eachWithIndex { itVsamFile, idx ->
//                def vsamJavaIdentifier = JavaUtils.normalizeJavaIdentifier(itVsamFile.fileName)
//                def rawCode = cobolFullVariables.find { itFullVar -> itFullVar.program == itVsamFile.program && itFullVar.name == itVsamFile.fileVariable }?.code
//                def vsamModel = [
//                        program: itVsamFile.program,
//                        name   : vsamJavaIdentifier,
//                        code   : rawCode,
//                        file   : vsamJavaIdentifier + ".java"
//                ]
//                models.add(vsamModel)
//            }
            println "TableModels: ${models.collect { tableModel -> tableModel.name }}"

            def dtosFiles = dtos.collect { it.file }?.unique()
            def modelsFiles = models.collect { it.file }?.unique()
            def pojosFiles = pojos.collect { it.file }?.unique()

            def paragraphsFlow = cypherJolt(summarization.cobolParagraphsFlowCypherQuery as String, summarization.joltNeo4jSingleObjectsToJson as String)
            project.put('paragraphsFlow', Utils.convertToConcurrent(paragraphsFlow))

            def paragraphs = report.paragraph.collect { it ->
                String programName = ((String) it.name).split("\\.")[0]
                String paragraph = ((String) it.name).split("\\.")[1]
                def paragraphCode = matchedCobolPrograms.collect { prog ->
                    prog.procedure_division.paragraphs.find { p -> p.name == paragraph }?.raw_code
                }.find { it != null } ?: '<NOT_FOUND>'

                def children = it.children.collect { entry ->
                    def parts = entry.split(/\./)
                    return [
                            program  : parts[0],
                            paragraph: parts[1]
                    ]
                }

                return [program: programName, paragraph: paragraph, code: paragraphCode, children: children]
            }

            project.put('dtos', Utils.convertToConcurrent(dtos))
            project.put('dtosFiles', Utils.convertToConcurrent(dtosFiles))
            project.put('models', Utils.convertToConcurrent(models))
            project.put('modelsFiles', Utils.convertToConcurrent(modelsFiles))
            project.put('pojos', Utils.convertToConcurrent(pojos))
            project.put('pojosFiles', Utils.convertToConcurrent(pojosFiles))
            project.put('locals', Utils.convertToConcurrent(locals.findAll { itLocal -> itLocal.code != null && itLocal.code != '<NOT_FOUND>' }))

            project.put('paragraphs', Utils.convertToConcurrent(paragraphs))

            projectContext.remove('project')
        }

        return output
    }

    Object summaryEvaluation(Map<String, Object> projectContext, String content, Path relativePath) {
        def attempts = 0
        def result = ""
        def recipe = projectContext.recipe
        while (attempts++ < 3) {
            try {
                List<TransformDto> history = new ConcurrentLinkedList<>()
                def filePath = FileUtils.absolutePathJoin(projectContext.rootFolder, relativePath)
                if (false && recipe.cacheMap?.containsKey(filePath.toString())) {
                    println "CACHE - Retrieving from cache: ${filePath.toString()}"
                    result = recipe.cacheMap.get(filePath.toString())
                    println "CACHE - Retrieving from cache: ${filePath.toString()}"
                } else {
                    result = scriptService.autoEval(content, history) as String
                    result = Utils.extractMarkdownCode(result) as String
                }
                FileUtils.writeFile(filePath, result, false)
                projectContext.putIfAbsent("files_metadata", new ConcurrentLinkedHashMap<>())
                def filesMetadata = projectContext.get("files_metadata") as ConcurrentLinkedHashMap<String, Object>
                filesMetadata.put(relativePath.toString(), [history: history])
                break
            } catch (HttpServerErrorException ignored) {
                println "HttpServerErrorException during the attempt ${attempts} of 3. Waiting 1 minute before retrying!"
                Thread.sleep(60000)
            } catch (Exception ex) {
                println "beforeEachMicroserviceEval Exception: ${ex.getMessage()}"
                throw ex
            }
        }

        return result
    }
}