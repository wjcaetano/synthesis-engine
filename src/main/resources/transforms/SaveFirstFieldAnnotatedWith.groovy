import com.capco.brsp.synthesisengine.service.ITransform
import org.springframework.context.ApplicationContext
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.FieldDeclaration
import com.capco.brsp.synthesisengine.utils.Utils

class SaveFirstFieldAnnotatedWithTransform implements ITransform {
    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        try {
            def params = Utils.splitParams(transformParams)
            def annotation = params.removeFirst() as String
            def path = params.removeFirst() as String

            System.out.println("Annotation: ${annotation} - Path: ${path}".toString())

            String fieldType = "Object"
            try {
                JavaParser javaParser = new JavaParser()
                CompilationUnit cu = javaParser.parse(content).getResult().get()
                FieldDeclaration fieldDeclaration = cu.findAll(FieldDeclaration.class).stream()
                        .filter(field -> field.getAnnotations().stream().anyMatch(ann -> ann.getNameAsString().equalsIgnoreCase(annotation)))
                        .findFirst().orElse(null)
                fieldType = fieldDeclaration.getVariable(0).getType()
            } finally {
                Utils.anyCollectionSet(projectContext, path, fieldType)
            }
        } catch (Exception ex) {
            System.out.println("SaveFirstFieldAnnotatedWithTransform - Path: ${annotation} - FieldType: ${fieldType}".toString())
            ex.printStackTrace()
        } finally {
            return content
        }
    }
}