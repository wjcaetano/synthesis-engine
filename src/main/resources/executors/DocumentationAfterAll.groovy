import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.tools.ToolsFunction
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import org.springframework.context.ApplicationContext

class DocumentationAfterAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)
        def toolsFunction = applicationContext.getBean(ToolsFunction.class)

        final def recipe = projectContext.recipe as Map<String, Object>
        if (projectContext.$api.configs.options.export == "sendToConfluence") {
            try {
                final def documentsPath = FileUtils.absolutePathJoin(
                        projectContext.rootFolder,
                        projectContext?.projects[0].projectNormalizedName,
                        "by-" + projectContext.$api.configs.options.persona + '-and-' + projectContext.$api.configs.options.generateBy,
                        "documents").toString()
                final def allFiles = FileUtils.crawlDirectory(documentsPath)
                final def mapOfFiles = allFiles.collectEntries { it -> [(it.path): it.content] } as Map<String, String>

                final def confluenceSpaceKey = scriptService.autoEval(recipe.vars.confluenceSpaceKey as String) as String
                final def confluenceURL = scriptService.autoEval(recipe.vars.confluenceURL as String) as String
                final def confluenceFolderID = scriptService.autoEval(recipe.vars.confluenceFolderID as String) as String
                final def confluenceUser = scriptService.autoEval(recipe.vars.confluenceUser as String) as String
                final def confluenceToken = scriptService.autoEval(recipe.vars.confluenceToken as String) as String
                toolsFunction.confluence(mapOfFiles, confluenceUser, confluenceToken, confluenceURL, confluenceFolderID, confluenceSpaceKey)
            } catch (Exception e) {
                throw new RuntimeException("Error in sending content to Confluence: " + e.message)
            }
        } else
            println "Content not sent to Confluence."
    }
}