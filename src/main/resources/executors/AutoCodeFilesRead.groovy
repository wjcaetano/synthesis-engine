import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

import java.util.regex.Pattern
import java.nio.file.Paths

class AutoCodeFilesRead implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    def listFilesRecursive(String path, List<Pattern> ignorePatterns) {
        def files = []
        def baseDir = new File(path)

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            println "Invalid directory: $path"
        } else {
            baseDir.eachFileRecurse { file ->
                def relativePath = Paths.get(path).toUri().relativize(file.toURI()).getPath()

                if (!matchesIgnore(relativePath, ignorePatterns)) {
                    if (file.isFile()) {
                        files += file.absolutePath
                    }
                }
            }
        }

        return files
    }

    def matchesIgnore(String relativePath, List<Pattern> ignorePatterns) {
        for (pattern in ignorePatterns) {
            if (pattern.matcher(relativePath).matches()) {
                return true
            }
        }
        return false
    }

    def parseIgnorePatterns(String ignoreContent) {
        def patterns = []
        ignoreContent.eachLine { line ->
            def trimmed = line.trim()
            if (!trimmed || trimmed.startsWith("#")) return

            String regex
            if (trimmed.endsWith("/")) {
                // Directory pattern: match anything starting with that path
                regex = Pattern.quote(trimmed) + ".*"
            } else {
                // Wildcard/filename pattern
                regex = trimmed
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".")
            }

            patterns << Pattern.compile("^${regex}\$")
        }

        return patterns
    }

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def content = projectContext.content?.trim() as String
        def rootFolder = projectContext.rootFolder as String
        def ignoreText = projectContext.recipe.templates?.ignorePattern as String
        def path = Utils.evl(content, rootFolder)

        def ignorePatterns = parseIgnorePatterns(ignoreText ?: "")
        return JsonUtils.writeAsJsonString(listFilesRecursive(path, ignorePatterns), true)
    }
}