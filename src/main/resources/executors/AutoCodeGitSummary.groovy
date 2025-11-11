import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.tools.ToolsFunction
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class AutoCodeGitSummary implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()
    ToolsFunction toolsFunction = null

    def git_summary(String path) {
        def gitDir = new File(path, ".git")
        if (!gitDir.exists()) {
            return "Not a git repository: ${path}"
        }

        def currentBranch = toolsFunction.shellRun("Windows", "git rev-parse --abbrev-ref HEAD")
        def mainBranch = toolsFunction.shellRun("Windows", "git symbolic-ref refs/remotes/origin/HEAD")?.split("/")?.last() ?: "master"

        def statusLines = toolsFunction.shellRun("Windows", "git status --short")?.readLines() ?: []
        def formattedStatus = statusLines ? statusLines.collect { "  ${it}" }.join("\n") : "  clean"

        def commits = toolsFunction.shellRun("Windows", "git log --pretty=format:\"%h %s\" -n 4")?.readLines() ?: []
        def formattedCommits = commits.collect { "  ${it}" }.join("\n")

        return """
            Current branch: ${currentBranch}
            
            Main branch (you will usually use this for PRs): ${mainBranch}
            
            Status:
            ${formattedStatus}
            
            Recent commits:
            ${formattedCommits}
            """
    }

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)
        this.toolsFunction = applicationContext.getBean(ToolsFunction.class)

        def content = projectContext.content?.trim() as String
        def rootFolder = projectContext.rootFolder as String
        def path = Utils.evl(content, rootFolder)

        return git_summary(path)
    }
}