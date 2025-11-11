import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.springframework.context.ApplicationContext

import java.nio.file.Path

class SplitInnerClasses implements ITransform {
    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        JavaParser javaParser = new JavaParser()

        def mainPackage = "package exception;"
        def innerClasses = [:]
        def classDefinition = new StringBuilder()
        try {
            var compilationUnit = javaParser.parse(content).getResult().get()
            if (compilationUnit.getPackageDeclaration().isPresent()) {
                mainPackage = compilationUnit.getPackageDeclaration().get().toString()
            }

            classDefinition.append(mainPackage).append("\n")
            classDefinition.append(mainPackage.replace(";", ".*;").replace("package", "import")).append("\n")
            classDefinition.append(compilationUnit.getImports().join("")).append("\n")
            classDefinition.append(compilationUnit.getAllComments().join("")).append("\n")

            compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(classOrInterface -> {

                if (classOrInterface.getComment().isPresent()) {
                    classDefinition.append(classOrInterface.getComment().get().toString()).append("\n")
                }
                classDefinition.append(classOrInterface.getAnnotations().join("\n")).append("\n")
                classDefinition.append(classOrInterface.getModifiers().join(" ")).append(" ")
                classDefinition.append(classOrInterface.getNameAsString()).append(" ")
                classDefinition.append(classOrInterface.getTypeParameters().join(", ")).append(" ")
                var extendsValues = classOrInterface.getExtendedTypes()
                if (!extendsValues.isEmpty()) {
                    classDefinition.append(" extends ").append(extendsValues.join(", ")).append(" ")
                }

                var implementsValues = classOrInterface.getImplementedTypes()
                if (!implementsValues.isEmpty()) {
                    classDefinition.append(" implements ").append(implementsValues.join(", ")).append(" ")
                }

                classDefinition.append("{\n")

                classOrInterface.getMembers().forEach(member -> {
                    if (member instanceof ClassOrInterfaceDeclaration) {
                        ClassOrInterfaceDeclaration classMember = member as ClassOrInterfaceDeclaration
                        innerClasses.put(classMember.getNameAsString(), mainPackage + "\n\n" + classMember.toString())
                    } else {
                        classDefinition.append(indent(member.toString(), 4))
                    }
                })

                classDefinition.append("\n}")
            })
        } catch (Exception ignored) {
            return content
        }

        final Path filePath = FileUtils.absolutePathJoin(projectContext.rootFolder, projectContext.cluster.clusterNormalizedName, projectContext.filePath)
        def folderPath = filePath.getParent()
        innerClasses.each { it ->
            {
                def className = it.key as String
                def classContent = it.value as String
                var newFilePath = FileUtils.absolutePathJoin(folderPath, className + ".java")
                FileUtils.writeFile(newFilePath, classContent, false)
            }
        }


        return classDefinition.toString()
    }

    private static String indent(String text, int spaces) {
        String indent = " ".repeat(spaces)
        return text.lines()
                .map(line -> indent + line)
                .reduce((line1, line2) -> line1 + "\n" + line2)
                .orElse("")
    }
}
