import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.JavaUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class CheckAndFixEachMethodTransform implements ITransform {
    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        def superService = applicationContext.getBean(SuperService.class)

        transformParams = superService.XGT(transformParams)

        def attempts = 0
        def newContent = content
        def exception = null
        while (attempts++ < 3) {
            try {
                var promptOfOriginalCode = """
                    Just ACKNOWLEDGE if you received and understand the COBOL code below:
                    ${transformParams}
                """.toString()
                superService.BCF(projectContext, promptOfOriginalCode)

                var prompt = """
                    Being a Java Spring Boot specialist, and a seasoned COBOL to JAVA translator, 
                    give me a list of methodNames that are still missing some business logic or needs to be adjusted. Just the names separated by comma, like below:
                    
                    method1, method2, method3
        
                    Please don't include anything else in your answer!
                    
                    Otherwise, return just an OK message (exactly these two chars OK only, nothing more).
                    
                    ################# CODE TO CHECK
                    
                    ${content}
                """.toString()

                var poorMethods = superService.BCF(projectContext, prompt)
                if (poorMethods == "OK") {
                    return content
                }

                var poorMethodsList = poorMethods.split(",").collect { it.trim() }

                var extractedMarkdown = (String) Utils.extractMarkdownCode(content)
                var listAllMethods = JavaUtils.listAllMethods(extractedMarkdown).findAll { it -> poorMethodsList.any {it2 -> it.matches(it2) } }
                for (String method : listAllMethods) {
                    String repromptText = """
                        1. Improve the method being aware of any DTO, REPOSITORY, ENTITY MODEL available at the context!
                        2. Take advantage of the current existing comments inside of the original method below.
                        3. The new content needs to fit the same method signature, don't generate other helper methods for it!
                        4. Encapsulate all code within a try-catch block.
                        5. Include a JavaDoc to the method.
                        6. Include any import needed.
                        7. Use a standard class/primitive type as return type or any of the DTOs listed.
                        8. You are not allowed to generate methods with more than 30 lines of code, whenever it gets over that size split the method in comprehensive pieces without touching the original method
                        signature that you are refactoring. Any of the additional methods needs to be declared as private.
                        9. Remove any comments inside the method! They are not allowed!
                        10. Every method that is supposed to change a value from another scope needs to return a value or save it on the respective repository/file already been used by the service.
                        
                        ORIGINAL METHOD CONTENT:
                        
                        ${method}
                        """.toString()
                    String newMethodContent = (String) Utils.extractMarkdownCode(superService.BCF(projectContext, repromptText))
                    newContent = newContent.replace(method, newMethodContent)
                }
                exception = null
                break
            } catch (Exception ex) {
                exception = ex
                System.out.println("Exception during the attempt ${attempts} of 3. Waiting 1 minute before retrying!".toString())
                Thread.sleep(60000)
            }
        }

        if (exception != null) {
            throw exception
        }

        return newContent
    }
}