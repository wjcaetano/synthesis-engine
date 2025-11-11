import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import org.springframework.context.ApplicationContext

class AccumulateImportsAndJava implements ITransform {
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        def project = projectContext?.project as Map<String, Object>
        def strFullFilePath = projectContext.fullFilePath?.toString()
        def fileName = projectContext?.fileName as String

        if (fileName == null || strFullFilePath == null || project == null) {
            println "Skipping AccumulateImportsAndJava for the current item because some fields (fileName, project, strFullFilePath) are missing!"
            return content
        }

        Set<String> importLines = []
        if (content != null && !content.isEmpty()) {
            content.eachLine { line ->
                def trimmedLine = line.trim()
                if (trimmedLine.startsWith("import")) {
                    importLines << line
                }
            }
            List<String> uniqueImportLines = new ConcurrentLinkedList<>(importLines)
            def listOfImports = (project.get('imports') ?: []) as List<String>
            listOfImports.addAll(uniqueImportLines)
            project.put('imports', listOfImports)
        }

        if (!content.startsWith("// Skipped/Empty File")) {
            if (strFullFilePath.contains('java') && !strFullFilePath.contains('diagrams')) {
                if (project.javaFiles == null) {
                    project.javaFiles = new ConcurrentLinkedList<>()
                }
                def javaFileContent = "############# File Name: ${fileName}\n${content ?: ''}\n\n".toString()
                project.javaFiles.add(javaFileContent)
                println "Accumulating java files: ${strFullFilePath}"
            } else {
                println "Isn't a java file: ${strFullFilePath}"
            }
        }

        return content
    }
}
