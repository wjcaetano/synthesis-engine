import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.JavaUtils
import com.github.javaparser.ParseProblemException
import org.springframework.context.ApplicationContext

class CreateServices implements IExecutor {

    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()
    List<String> originalContexts = new ConcurrentLinkedList<>()

    String handlePrompt(Map<String, Object> projectContext, String prompt) {
        println "CreateServicePrompt - Prompt size: ${prompt.length()}".toString()
        try {
            return scriptService.handleAgent(projectContext, prompt)
        } catch (Exception ignored) {
            println "CreateServicePrompt - Failed - Probably due to a stuck thread - Trying to restart a new one".toString()
            scriptService.autoEval("@@@closellmthread")
            scriptService.autoEval("@@@openllmthread")
            println "CreateServicePrompt - New Thread - Contextualizing with scaffold data".toString()
            scriptService.handleAgent(projectContext, "Consider the content below as part of your context to be aware of\n\n:" + originalContexts.join("\n\n"))

            StringBuilder NewMethodsContext = new StringBuilder("Consider also the methods that was previously created below:\n\n")
            def newMethods = projectContext.project?.NewMethods as Map<String, Map<String, Object>>
            newMethods?.eachWithIndex { Map.Entry<String, Map<String, Object>> programs, int programIndex ->
                programs.eachWithIndex{ Map.Entry<String, String> programMethodEntry, int programMethodsIndex ->
                    def methodName = programMethodEntry.getKey()
                    def methodContent = programMethodEntry.getValue()
                    NewMethodsContext.append(methodContent).append("\n\n")
                }
            }

            println "CreateServicePrompt - New Thread - Contextualizing with methods already created before".toString()
            scriptService.handleAgent(projectContext, NewMethodsContext.toString())

            println "CreateServicePrompt - New Thread - Successfully contextualized! Now continuing from where it stopped!".toString()
            return scriptService.handleAgent(projectContext, prompt)
        }
    }

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {

        println "Creating class Services"
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

//        def program = "CUSTTRN2"
//        def program = "CUSTVSAM"
//        def className = normalizeClassName(program)
//        def classCode = createClass(program, className, projectContext)


        def project = projectContext.project as Map<String, Object>
        project.programsNames.each { String program ->
            //            println program
            def totalParagraphs = project.paragraphsFlow.findAll{
                it.program == program &&
                it.exitParagraph != true &&
                it.emptyParagraph != true
            }.size()

            project.put("currentMethodCount", 0)
            project.put("totalMethods", totalParagraphs)

            def className = normalizeClassName(program)
            def classCode = createClass(program, className, projectContext)
        }

        return ""
    }

    def createClass(String program, String className, Map<String, Object> projectContext){

        def classScaffold = createScaffoldClass(program, className, projectContext)

        createContext(program, classScaffold, projectContext)

        def project = projectContext.project as Map<String, Object>
        def mainParagraph = project.paragraphsFlow.find{it.program==program}
        //        def mainParagraph = project.paragraphsFlow.find{it.paragraph=="721-COPY-RECORDS"}

        createMethods(program, className, mainParagraph, projectContext)

        def classCode = replaceMethodsInScaffold(program, className, classScaffold, projectContext)

        Utils.anyCollectionSet(project, 'NewClasses.'+program, classCode)
        def filePath = FileUtils.absolutePathJoin(
                projectContext.rootFolder,
                project.projectNormalizedName,
                "src/main/java/com/capco/",
                project.projectNormalizedName,
                "service", className + "Service.java")
        FileUtils.writeFile(filePath, classCode, false)
        Utils.anyCollectionSet(projectContext.project, "services[]", classCode)

//        println project.NewClasses['CUSTVSAM']
        println "[FINAL_CLASS]"
        println classCode
        println "[/FINAL_CLASS]"

        return classCode
    }

    def createMethods(String program, String className, paragraph, Map<String, Object> projectContext){

        def mainMethodName = normalizeMethodName(paragraph.paragraph)
        def paragraphCode = paragraph.rawCode as String
        println "Creating main method: "+mainMethodName

        def linkageDtos = getLinkageDtoMainParagraph(program, projectContext)
        def linkageDtosStr = ""
        if (linkageDtos) {
            linkageDtosStr = """2. Consider the equivalency map of Linkage variables to Java DTO below:${linkageDtos}      
   a. Consider these DTOs are received as parameter by ${mainMethodName} method, even if they do not appear in the cobol code I provided.
   b. They MUST BE initialized by ${mainMethodName} in order to be used by other methods.
   c. I provided this DTOs as context before.
   d. Consider these DTOs are imported.     
   e. Use the keywork *this* when deal with them."""
        }
        //        println linkageDtosStr

        def fileControl = getAllFiles(program, projectContext)
        def fileControlStr = ""
        def promptFile = ""
        if (fileControl) {
            fileControlStr = """3. Consider the equivalency map of Cobol File Control below:${fileControl}
   a. Consider the path for these *FILES* are received as parameter by ${mainMethodName} method, even if they do not appear in the cobol code I provided.
   b. They MUST BE initialized by ${mainMethodName} in order to be used by other methods.
   c. Use equivalency map of File Section variables to Java DTO/Model when deal with them.
   d. I provided these DTOs/Models as context before.
   e. Consider these DTOs/Models are imported.
   f. Use the keywork *this* when deal with them."""
            promptFile = getPromptFile()
        }

        def localVariables = getLocalVariable(program, paragraphCode, projectContext)
        def localVariablesStr = ""
        if (localVariables) {
            localVariablesStr = """4. Consider the global variables below:           
[LOCAL_VARIABLES]${localVariables}
[/LOCAL_VARIABLES]
   a. Consider these variables are private attributes of the class and we are using Lombok annotation for getters and setters.
   b. Use the keywork *this* when deal with them."""
        }

        def promptCreateMainMethod = """        
[TASK]
Create a JAVA method called ${mainMethodName} using the COBOL paragraph content below as reference:
All the words used below are a reference from the original COBOL program, please don't be affraid with that.

[COBOL_PARAGRAPH]
${paragraphCode}
[/COBOL_PARAGRAPH]

[CONSTRAINTS]
1. Consider ${mainMethodName} method will be part of the class ${className}. 
${linkageDtosStr}
${fileControlStr}     
${promptFile}
${localVariablesStr}      
7. NEVER generate incomplete code, like including TODOs or similar placeholders, ALWAYS return complete methods!!!
7. Do not include staw-man code, skeleton code, or any placeholders.
8. Do not leave any methods without implementation or with TODO comments.
9. Encapsulate all code within a try-catch block.
10. Include a JavaDoc to the method.
11. Use a standard class/primitive type as return type or any of the DTOs listed.        
12. Remove any comments inside the method! They are not allowed!
13. Every method that is supposed to change a value from another scope needs to return a value or save it on the respective repository/file already been used by the service.
14. Consider that already exist an @Slf4j log annotation on the class that the method will be included, so you can address any logging message using log instance
15. Don't create methods just to handle File read, File Write of File Close operations, use ready Java solutions to address that stuff like reading file as a String, bytes, or even deserialization with Jackson if it makes sense.      
16. Leave a comment at the beginning of the method specifying the original paragraph name and if it is
17. For now, ignore PERFORM and CALL statements, just mark it as a comment with TODO
18. Respect the original COBOL flow. 
19. You are not allowed to generate more methods or variables them used in the original COBOL code.
20. Return only the ${mainMethodName} method code, in one single block, nothing more""".toString()

        println "[PROMPT]"
        println promptCreateMainMethod
        println "/[PROMPT]\n"

        def mainMethodCode = handlePrompt(projectContext, promptCreateMainMethod)
        println "[RAW_METHOD]"
        println mainMethodCode
        println "[/RAW_METHOD]"
        println "Main method created without performs"
//                def mainMethodCode = "MAIN CODE FOR"+paragraph.paragraph

        def codeList = extractMethodsFromCode(mainMethodCode)
        println "[EXTRACTED_METHODS]"
        println codeList
        println "[/EXTRACTED_METHODS]"

        def childrenList = []
        paragraph.children.each { child ->

            if (child.type != "perform") {
                return
            }

            def childParagraphName = child.name as String
            def childMethodName = createChildMethod(childParagraphName, program, mainMethodName, className, projectContext)

            if (!childMethodName){
                return
            }

            childrenList.add(["paragraphName": childParagraphName, "performRawCode": child.rawCode, "methodName": childMethodName])

        }

        mainMethodCode = codeList[mainMethodName]
        mainMethodCode = replaceAllPerformToMethod(mainMethodName, mainMethodCode, childrenList, projectContext)
        mainMethodCode = Utils.extractMarkdownCode(mainMethodCode) as String
        codeList[mainMethodName] = mainMethodCode

        println "[METHOD_FINAL]"
        println codeList[mainMethodName]
        println "[/METHOD_FINAL]"
        println "Main method completed"

        saveMethods(program, mainMethodName, codeList, projectContext)

    }

    def createChildMethod(String paragraphName, String program, String mainMethodName, String className, Map<String, Object> projectContext) {
        def methodName = normalizeMethodName(paragraphName)
        def project = projectContext.project as Map<String, Object>
        def currentCount = project.currentMethodCount + 1
        project.put("currentMethodCount", currentCount)

        println "*******************************************************************************"
        println "Creating method [${currentCount}/${project.totalMethods}] for paragraph ${paragraphName} from program ${program}"
        println "New method name: " + methodName
        println "*******************************************************************************"

        if (project.NewMethods){
            //            println "achei NewMethods"
            if (project.NewMethods[program]) {
                //                println "achei program"
                //                println "buscando "+methodName
                def exists = project.NewMethods[program].find{key, value -> key == methodName}
                if (exists) {
                    println "${methodName} Already exisits - returning it"
                    return methodName
                }
            }
        }

        def paragraphFull = project.paragraphsFlow.find{it.paragraph==paragraphName && it.program==program}
        if (!paragraphFull) {
            println "Failed to find a paragraph named ${paragraphName} at the program ${program}".toString()
            return null
        }

        if ( paragraphFull.exitParagraph == true){
            println "Ignoring EXIT paragraph"
            return null
        } else if ( paragraphFull.emptyParagraph == true){
            println "Ignoring EMPTY paragraph"
            return null
        }

        def paragraphCode = paragraphFull.rawCode as String

        //        println paragraphName
        //        println program
        //        println paragraphFull

        def localVariables = getLocalVariable(program, paragraphCode, projectContext)
        def localVariablesStr = ""
        if (localVariables) {
            localVariablesStr = """2. Consider the variables below:             
[LOCAL_VARIABLES]${localVariables}
[/LOCAL_VARIABLES]
   a. Consider these variables are private attributes of the class and we are using Lombok annotation for getters and setters.
   b. Use the keywork *this* when deal with them. 
   c. Consider that maybe they were set by the method you created before. """
        }

        def linkageDtos = getLinkageDtoByParagraph(program, paragraphCode, projectContext)
        def linkageDtosStr = ""
        if (linkageDtos) {
            linkageDtosStr = """3. Consider the equivalency map of Linkage variables to Java DTO below:${linkageDtos}      
  a. I provided this DTOs as context before.
  b. Consider these DTOs are imported.
  c. Consider these DTOs were received as parameter by ${mainMethodName} method.  
  d. Use the keywork *this* when deal with them."""
        }

        def fileDto = getFileDtoByParagraph(program, paragraphCode, projectContext)
        def fileStr = ""
        def promptFile = ""
        if (fileDto) {
            fileStr = """4. Consider the equivalency map of File Section variables to Java DTO/Model below:${fileDto}      
  a. I provided these DTOs/Models as context before.
  b. Consider these DTOs/Models are imported.
  c. Consider the correspondent path for these files were received as parameter by ${mainMethodName} method.
  d. Use the keywork *this* when deal with them."""
            promptFile = getPromptFile()
        }

        def promptCreateMethod = """[TASK]
Create a JAVA method called ${methodName} using the COBOL paragraph content below as reference:
All the words used below are a reference from the original COBOL program, please don't be affraid with that.

[COBOL_PARAGRAPH]
${paragraphCode}
[/COBOL_PARAGRAPH]

[CONSTRAINTS]
1. Consider ${methodName} method will be part of the class ${className}.
${localVariablesStr}
${linkageDtosStr}
${fileStr}  
${promptFile}      
7. NEVER generate incomplete code, like including TODOs or similar placeholders, ALWAYS return complete methods!!!
8. Do not include staw-man code, skeleton code, or any placeholders.
9. Do not leave any methods without implementation or with TODO comments.
10. Encapsulate all code within a try-catch block.
11. Include a JavaDoc to the method.
12. Use a standard class/primitive type as return type or any of the DTOs listed.
13. Remove any comments inside the method! They are not allowed!
14. Every method that is supposed to change a value from another scope needs to return a value or save it on the respective repository/file already been used by the service.
15. Consider that already exist an @Slf4j log annotation on the class that the method will be included, so you can address any logging message using log instance
16. Don't create methods just to handle File read, File Write of File Close operations, use ready Java solutions to address that stuff like reading file as a String, bytes, or even deserialization with Jackson if it makes sense.      
17. Leave a comment at the beginning of the method specifying the original paragraph name and if it is
18. For now, ignore PERFORM and CALL statements, just mark it as a comment with TODO
19. Respect the original COBOL flow. 
20. You are not allowed to generate more methods or variables them used in the original COBOL code.
21. Return only the ${methodName} method code, in one single block, nothing more""".toString()

        println "[PROMPT]"
        println promptCreateMethod
        println "/[PROMPT]\n"

        def methodCode = handlePrompt(projectContext, promptCreateMethod).toString()
//                def methodCode = "My new code for " + methodName
        println "[RAW_METHOD]"
        println methodCode
        println "[/RAW_METHOD]"

        def childrenList = []
        paragraphFull.children.each { child ->

            if (child.type != "perform") {
                return
            }

            def childParagraphName = child.name as String
            def childMethodName = createChildMethod(childParagraphName, program, mainMethodName, className, projectContext)

            if (!childMethodName){
                return
            }

            childrenList.add(["paragraphName": childParagraphName, "performRawCode": child.rawCode, "methodName": childMethodName])
        }

        def codeList = extractMethodsFromCode(methodCode)
        println "[EXTRACTED_METHODS]"
        println codeList
        println "[/EXTRACTED_METHODS]"

        methodCode = codeList[methodName]
        methodCode = replaceAllPerformToMethod(methodName, methodCode, childrenList, projectContext)
        methodCode = Utils.extractMarkdownCode(methodCode) as String
        codeList[methodName] = methodCode

        println "[METHOD_FINAL]"
        println codeList[methodName]
        println "[/METHOD_FINAL]"

        println methodName + " method completed"

        saveMethods(program, methodName, codeList, projectContext)

        return methodName

    }

    def replaceAllPerformToMethod(String parentMethodName, String parentMethodCode, childrenList, Map<String, Object> projectContext) {
        // Sometimes the llm generates methods with parameters in the signature
        // reason why Im still sending the performs replacement to LLM do

        childrenList.unique()

        if (childrenList.size() == 0) {
            return parentMethodCode
        }

        def replaceList = ""
        childrenList.each { child ->

            if (child.performRawCode.contains('THRU')){
                replaceList += "Add call to ${child.methodName} method.\n"
            } else {
                replaceList += "Replace PERFORM ${child.paragraphName} to call ${child.methodName} method.\n"
            }
        }

        def prompt = """
[TASK]
Update the method *${parentMethodName}* adding call to new methods or replacing the *PERFORMs* statements/comments according to the perform list bellow.

[${parentMethodName}_CODE]
${parentMethodCode}
[/${parentMethodName}_CODE]

[PERFORM_LIST]
${replaceList}
[/PERFORM_LIST]

Detailed Instructions:
1. NEVER generate incomplete code, like including TODOs or similar placeholders, ALWAYS return complete method!!!
2. Do not include staw-man code, skeleton code, or any placeholders.
3. Consider all methods are part of the same class, so use the keywork *this* when deal with them.
4. Return only the updated ${parentMethodName} code, in one single block, nothing more.""".toString()

        parentMethodCode = handlePrompt(projectContext, prompt)

        return parentMethodCode
    }

    def replacePerformToMethodOneByOne(String parentMethodName, String parentMethodCode, String childMethodName, String childParagraphName, String childMethodCode, Map<String, Object> projectContext) {
        // Doesnt work well when parent method has many performs


        if (!parentMethodCode.toLowerCase().contains("perform ${childParagraphName}")){
            println "PERFORM ${childParagraphName} already replaced in ${parentMethodName}"
            return parentMethodCode
        }

        def prompt = """
            [TASK]
            Update the method *${parentMethodName}* replacing the *PERFORM ${childParagraphName}* statement/comment for call the method ${childMethodName}.
            
            [${parentMethodName}_CODE]
            ${parentMethodCode}
            [/${parentMethodName}_CODE]
            
            [${childMethodName}_CODE]
            ${childMethodCode}
            [/${childMethodName}_CODE]
            
            Detailed Instructions:
            1. NEVER generate incomplete code, like including TODOs or similar placeholders, ALWAYS return complete method!!!
            2. Do not include staw-man code, skeleton code, or any placeholders.
            3. Replace only the PERFORM ${childParagraphName}, keep other performs untouched
            4. Consider both methods are part of the same class, so use the keywork *this* when deal with them.
            5. Return only the updated ${parentMethodName} code, in one single block, nothing more.""".toString()


        parentMethodCode = handlePrompt(projectContext, prompt)
        //println "MAIN COM PERFORM"
        //        println parentMethodName
        //println "******************************"

        return parentMethodCode
    }

    def replaceMethodsInScaffold(String program, String className, String classCode, Map<String, Object> projectContext, deterministic=true){

        def project = projectContext.project as Map<String, Object>
        if (!project.NewMethods) {
            println "NewMethods not found"
            return
        }

        def methods = project.NewMethods[program]
        if (!methods) {
            println program + " not found in NewMethods"
            return
        }

        if (deterministic == true){
            println "Adding methods to ${className} class"
            classCode = replaceMethodsInScaffoldUsingReplace(program, methods, classCode, projectContext)
        } else {
            classCode = replaceMethodsInScaffoldUsingLLM(methods, className, classCode, projectContext)
        }

        return classCode
    }

    def replaceMethodsInScaffoldUsingReplace(String program, methods, String classCode, projectContext){

        def project = projectContext.project as Map<String, Object>

        project.paragraphsFlow.findAll{it.program == program}.each { paragraphs ->

            if ( paragraphs.exitParagraph == true ||  paragraphs.emptyParagraph == true){
                return
            }

            def methodName = normalizeMethodName(paragraphs.paragraph as String)

            // main code from method
            def methodCode = methods[methodName]

            // auxiliar methods
            methods.each { name, code ->
                if (name.contains("aux" + methodName)){
                    methodCode += "\n" + code
                }
            }

            classCode = classCode.replace("//TODO method " + methodName, "\n" + methodCode + "\n")
        }

        return classCode
    }

    def replaceMethodsInScaffoldUsingLLM(methods, String className, String classCode, Map<String, Object> projectContext){

        methods.each { method, methodCode ->

            println "Adding ${method} to ${className}"

            //            println method
            //            println methodCode
            def prompt = """
                [TASK]
                Update the class ${className} replacing the commented *TODO method ${method}*.
                
                [${className}_CODE]
                ${classCode}
                [/${className}_CODE]
                
                [${method}_CODE]
                ${methodCode}
                [/${method}_CODE]
                
                Detailed Instructions:
                1. NEVER generate incomplete code, like including TODOs or similar placeholders, ALWAYS return complete class
                2. Do not include staw-man code, skeleton code, or any placeholders.
                3. Replace only the method ${method}, keep others untouched
                4. Return only the updated ${className} class code, in one single block, nothing more.""".toString()

            //            print prompt

            classCode = handlePrompt(projectContext, prompt)
            //            print classCode
        }

        return classCode
    }

    def createContext(String program, String classScaffold, Map<String, Object> projectContext){

        println "Creating context for program " + program
        def dtos = getAllDtoClasses(program, projectContext)
        def pojos = getAllPojoClasses(program, projectContext)
        def models = getAllModelClasses(program, projectContext)

        def promptDtos = ""
        if (dtos) {
            promptDtos = """
Consider these DTOs bellow:${dtos}"""
        }

        def promptPojos = ""
        if (pojos){
            promptPojos = """
Consider these Pojos bellow:${pojos}"""
        }

        def promptModels = ""
        if (models){
            promptModels = """
Consider these Models and Repositories bellow:${models}"""
        }

        def prompt = """Consider my class scaffold: 
```java${classScaffold}
```
${promptDtos}
${promptPojos}
${promptModels}
All these assests you will use as context for the next iterations.
For now just answer yes meaning that you are aware.""".toString()
        //        println prompt

//        println "[CONTEXT]"
//        println prompt
//        println "[/CONTEXT]"

        def answer = handlePrompt(projectContext, prompt)
        originalContexts.add(prompt)
        println "Context created: " + answer
    }

    def createScaffoldClass(program, className, Map<String, Object> projectContext){

        def groupId = projectContext.recipe.vars.groupId
        def project = projectContext.project as Map<String, Object>
        def projectNormalizedName = project.projectNormalizedName


        def paragraphsStr = ""
        project.paragraphsFlow.findAll{it.program == program}.each { paragraphs ->

            if ( paragraphs.exitParagraph == true ){
                println "Ignoring EXIT paragraph when creating scaffold " + paragraphs.paragraph
                return
            } else if ( paragraphs.emptyParagraph == true ) {
                println "Ignoring EMPTY paragraph when creating scaffold " + paragraphs.paragraph
                return
            }

            def methodName = normalizeMethodName(paragraphs.paragraph as String)
            paragraphsStr += "\n        //TODO method " + methodName
        }

        def dtos = project.dtos.findAll{
            it.program == program
        }
        def dtosImport = ""
        dtos.each { dto ->
            dtosImport += """
        import ${groupId}.${projectNormalizedName}.domain.dto.${dto.javaName};"""
        }

        def pojos = project.pojos.findAll{
            it.program == program
        }
        def pojosImport = ""
        pojos.each { pojo ->
            pojosImport += """
        import ${groupId}.${projectNormalizedName}.domain.file.${pojo.name};"""
        }

        def models = project.models.findAll{
            it.program == program
        }
        def modelsImport = ""
        models.each { model ->
            modelsImport += """
        import ${groupId}.${projectNormalizedName}.domain.db.${model.name};
        import ${groupId}.${projectNormalizedName}.repository.${model.name}Repository;"""
        }

        def scaffold = """
        package ${groupId}.${projectNormalizedName}.service;        
        import ${groupId}.${projectNormalizedName}.exception.*;
        import ${groupId}.${projectNormalizedName}.service.*;
        import ${groupId}.${projectNormalizedName}.utils.*;
        ${dtosImport}
        ${pojosImport}
        ${modelsImport}
        import lombok.Getter;
        import lombok.Setter;

        @Service
        @Getter
        @Setter    
        public class ${className} {
            ${paragraphsStr}
        }"""

//        println "[SCAFOLDING]"
//        println scaffold
//        println "[/SCAFOLDING]"

        return scaffold
    }

    def getLocalVariable(String program, String paragraphCode, Map<String, Object> projectContext){
        // find Working Storage variables used in a paragraph

        def variables = []

        def project = projectContext.project as Map<String, Object>
        def cobolVariables = project.cobolVariables.findAll{
            it.program == program && it.label == "COBOLStorage"
        }
        cobolVariables.each { variable ->

            if (!paragraphCode.contains(variable.name as String)) {
                return
            }

            if (variable.name=='FILLER') {
                return
            }

            if (variable.parentName) {
                def completa = project.cobolFullVariables.find{
                    it.program == program &&
                    "COBOLStorage" in it.labels &&
                    (it.name==variable.parentName || variable.parentName in it.children)
                }
                variables.add(completa.code)
            } else if (variable.childCount>0) {
                def completa = project.cobolFullVariables.find{
                    it.program == program &&
                    "COBOLStorage" in it.labels &&
                    (it.name==variable.name || variable.name in it.children)
                }
                variables.add(completa.code)
            } else {
                variables.add(variable.rawCode)
            }

        }

        variables.unique()

        def variableOutput = new StringBuilder()
        variables.each { variable ->
            variableOutput << "\n[VARIABLE]\n"
            variableOutput << variable << "\n"
            variableOutput << "[/VARIABLE]"
        }

        return variableOutput.toString()
    }

    def getLinkageDtoMainParagraph(String program, Map<String, Object> projectContext){
        // find Linkage Section variables to be used by the MainParagraph

        def complexVariables = []

        def project = projectContext.project as Map<String, Object>
        def cobolVariables = project.cobolFullVariables.findAll{
            it.program == program && "COBOLLinkage" in it.labels
        }

        cobolVariables.each { variable ->

            def dto = project.dtos.find{
                it.name == variable.name && it.program == program
            }

            // so preciso do nome java das variaveis
            // a llm ja conhece os dtos pois mandei no contexto
            complexVariables.add(["javaName": dto.javaName, "code": variable.code])
        }

        complexVariables.unique()

        def variablesOutput = ""
        complexVariables.each{ variable ->
            variablesOutput += "\n[VARIABLE]\n"
            variablesOutput +=  variable.code
            variablesOutput += "\nEquivalent DTO: " + (variable.javaName as String)
            variablesOutput += "\n[/VARIABLE]"
        }

        return variablesOutput
    }

    def getLinkageDtoByParagraph(String program, String paragraphCode, Map<String, Object> projectContext){
        // find Linkage Section variables to be used by a certain Paragraph

        def complexVariables = []

        def project = projectContext.project as Map<String, Object>
        def cobolVariables = project.cobolVariables.findAll{
            it.program == program && it.label == "COBOLLinkage"
        }

        cobolVariables.each { variable ->

            if (!paragraphCode.contains(variable.name as String)) {
                return
            }

            if (variable.name=='FILLER') {
                return
            }

            def name = variable.name
            def code = variable.rawCode
            if (variable.parentName) {
                def completa = project.cobolFullVariables.find{
                    it.program == program &&
                            "COBOLLinkage" in it.labels &&
                            (it.name==variable.parentName || variable.parentName in it.children)
                }
                name = completa.name
                code = completa.code
            } else if (variable.childCount>0) {
                def completa = project.cobolFullVariables.find{
                    it.program == program &&
                            "COBOLLinkage" in it.labels &&
                            (it.name==variable.name || variable.name in it.children)
                }
                name = completa.name
                code = completa.code
            }

            def dto = project.dtos.find{
                it.name == name && it.program == program
            }

            // so preciso do nome java das variaveis
            // a llm ja conhece os dtos pois mandei no contexto
            complexVariables.add(["javaName": dto.javaName, "code": code])
        }

        complexVariables.unique()

        def variablesOutput = ""
        complexVariables.each{ variable ->
            variablesOutput += "\n[VARIABLE]\n"
            variablesOutput +=  variable.code
            variablesOutput += "\nEquivalent DTO: " + (variable.javaName as String)
            variablesOutput += "\n[/VARIABLE]"
        }

        return variablesOutput
    }

    def getAllDtoClasses(String program, Map<String, Object> projectContext){
        // find All DTOs Classes used in the whole program
        // DTOs correspond to Linkage variables
        // Used in the context

        def project = projectContext.project as Map<String, Object>
        def output = ""
        def dtos = project.dtos.findAll{
            it.program == program
        }
        dtos.each { dto ->
            def dtoClass = project.dtosClasses[dto.name as String]
            output += "\n" + dtoClass
        }

        return output
    }

    def getAllPojoClasses(String program, Map<String, Object> projectContext){
        // find All Pojo Classes used in the whole program
        // Pojo correspond to File Section record and files for flat files
        // Used in the context

        def project = projectContext.project as Map<String, Object>
        def output = ""
        def dtos = project.pojos.findAll{
            it.program == program
        }
        dtos.each { dto ->
            def dtoClass = project.pojosClasses[dto.name as String]
            output += "\n" + dtoClass
        }

        return output
    }

    def getAllModelClasses(String program, Map<String, Object> projectContext){
        // find All Model Classes used in the whole program
        // models correspond to File Section record and files for vsam files
        // Used in the context

        def project = projectContext.project as Map<String, Object>
        def output = ""
        def dtos = project.models.findAll{
            it.program == program
        }
        dtos.each { dto ->
            def dtoClass = project.modelsClasses[dto.name as String]
            output += "\n```java\n" + dtoClass + "```"
            def repository = project.repositories[dto.name as String]
            output += "\n```java\n" + repository + "```"
        }

        return output
    }


    def getAllFiles(String program, Map<String, Object> projectContext){
        // Find all Files used in the whole program
        // Used in MainParagraph to be the parameters
        // OPEN, CLOSE, READ, DELETE statements use file-name
        // WRITE, REWRITE statements use record-name

        def project = projectContext.project as Map<String, Object>
        def files = project.fileComprehensiveDetails.findAll{
            it.program == program
        }

        def variablesOutput = ""
        files.each{ file ->
            variablesOutput += "\n[FILE]"
            variablesOutput += "\nFile definition name: " + file.fileControlName
            variablesOutput += "\nFile path: " + file.filePathJCL
            variablesOutput += "\nRecord name: " + file.recordStorageName
            variablesOutput += "\nRecord definition:\n" + file.fullRawCode
            if (file.eligibleVariable=='WS') {
                variablesOutput += "\nEquivalent working storage record:\n" + file.rawCode
            }
            if (file.label == "COBOLVsamFile") {
                variablesOutput += "\nRespective Model: " + file.domainJavaName
            } else {
                variablesOutput += "\nRespective DTO:" + file.domainJavaName
            }
            variablesOutput += "\n[/FILE]"
        }

        return variablesOutput
    }

    def getFileDtoByParagraph(String program, String paragraphCode, Map<String, Object> projectContext){
        // Find all Files e File records used in a certain paragraph
        // OPEN, CLOSE, READ, DELETE statements always use file definition name
        // WRITE, REWRITE statements always use record-name

        def complexVariables = []

        // looking for file records
        def project = projectContext.project as Map<String, Object>
        def cobolVariables = project.cobolVariables.findAll{
            it.program == program && it.label == "COBOLFileRecordStorage"
        }

        cobolVariables.each { variable ->

            if (!paragraphCode.contains(variable.name as String)) {
                return
            }

            def file = project.fileComprehensiveDetails.find {
                it.program == program && it.fullRawCode.contains(variable.name)
            }

            if (!file){
                println "Record ${variable.name} not found in fileComprehensiveDetails for program ${program}"
                return
            }
//            print "achei" + file.filePathJCL

            // LLM already knows the DTOs from the context
            complexVariables.add([
                    "domainJavaName": file.domainJavaName,
                    "fileControlName": file.fileControlName,
                    "filePathJCL": file.filePathJCL,
                    "recordStorageName": file.recordStorageName,
                    "fileRawCode": file.fullRawCode,
                    "wsRawCode": file.rawCode,
                    "label": file.label,
                    "eligibleVariable": file.eligibleVariable
            ])
        }

        // looking for files
        def files = project.fileComprehensiveDetails.findAll{
            it.program == program
        }

        files.each { file ->

            if (!paragraphCode.contains(file.fileControlName as String) && !paragraphCode.contains(file.recordStorageName as String)) {
                return
            }

            complexVariables.add([
                    "domainJavaName": file.domainJavaName,
                    "fileControlName": file.fileControlName,
                    "filePathJCL": file.filePathJCL,
                    "recordStorageName": file.recordStorageName,
                    "fileRawCode": file.fullRawCode,
                    "wsRawCode": file.rawCode,
                    "label": file.label,
                    "eligibleVariable": file.eligibleVariable
            ])

        }

        complexVariables.unique()

        //        println complexVariables

        def variablesOutput = ""
        complexVariables.each{ variable ->
            variablesOutput += "\n[FILE]"
            variablesOutput += "\nFile definition name: " + variable.fileControlName
            variablesOutput += "\nFile path: " + (variable.filePathJCL as String)
            variablesOutput += "\nRecord name: " + variable.recordStorageName
            variablesOutput += "\nRecord definition:\n" + variable.fileRawCode
            if (variable.eligibleVariable=='WS') {
                variablesOutput += "\nEquivalent working storage record:\n" + variable.wsRawCode
            }
            if (variable.label == "COBOLVsamFile") {
                variablesOutput += "\nRespective Model : " + (variable.domainJavaName as String)
            } else {
                variablesOutput += "\nRespective DTO: " + (variable.domainJavaName as String)
            }
            variablesOutput += "\n[/FILE]"
        }
        //        println variablesOutput

        return variablesOutput
    }

    String getPromptFile(){

        def promptFile = """Consider the class already include a FileUtils helper class on the classpath that include a sort of different methods to be used replacing tasks for Opening, Closing, Reading and Writing files.
For any needs on that, please take advantage of the methods below:
- To OPEN a file and keep it open: 
  public static BufferedReader openReader(String fileName, String pathVariable)
  public static BufferedWriter openWriter(String fileName, String pathVariable)
- To CLOSE a file:
  public static void closeReader(BufferedReader reader)
  public static void closeWriter(BufferedWriter writer)
- To READ the whole content of a File to a List
  public static <T> List<T> FileUtils.readResourceFile(Class<T> clazz, String fileName)
- To READ a single line of content an parse it to a specific Model/DTO class:
  public static <T> void readLineByLine(Class<T> clazz, BufferedReader reader, Context context)
- To WRITE the whole content of a List<T> of objects to a File:
  public static <T> void FileUtils.writeResourceFile(Class<T> clazz, String fileName)
- To WRITE a single Model/DTO object to a line in the File:
  public static <T> void writeItem(Class<T> clazz, BufferedWriter writer, T item)

T is a reference to a generic class in Java, in this case it is expecting a Model/DTO class that should hold a single line of content of a file.
Any BufferReader or BufferWriter that you need to translate a paragraph into a Java Method that will not be closed at the end of it, should declare a variable
outside of the method scope following the convention:
  readBuffer{PascalCaseNameOfFileName}
  writeBuffer{PascalCaseNameOFileName}

Example:
  To a OPEN INPUT for the file named TRANSACTION-FILE -> readBufferTransactionFile

Also, any paragraph that is reading/writing/closing the content without taking care of the opening of that should consider that already exist on the class scope some buffer declared following the example above. """

        return promptFile
    }

    String normalizeMethodName(String paragraphName){
        def initialNum = paragraphName.find(/^\d+/)
        def methodName = (paragraphName).replaceAll(/^\d+/, '').replaceAll(/\$/, '').split('-').collect { it.toLowerCase().capitalize() }.join('')
        methodName = methodName[0].toLowerCase() + methodName.substring(1) + (initialNum ? initialNum : "")
        return methodName
    }

    String normalizeClassName(String program) {
        def initialNum = program.find(/^\d+/)
        def name = (program).split('-').collect { it.toLowerCase().capitalize().replaceAll(/^\d+/, '') }.join('')
        name = name + (initialNum ? initialNum : "") + "Service"
        return name
    }

    def extractMethodFromCode(String methodName, String methodCode, Map<String, Object> projectContext){

        try {
            methodCode = JavaUtils.extractMethodFromCode(methodCode, methodName) as String
        } catch (ParseProblemException ex) {
            println "Error when tryng extract method from code (first try):\n${ex.getMessage()}"
            try {
                methodCode = handlePrompt(projectContext, """
                    There's something wrong with the [JAVA CODE] syntax below. Based on the [EXCEPTION MESSAGE] and on your JAVA skills, please fix that, and return only ${methodName} method code for me!

                    [EXCEPTION MESSAGE]
                    ${ex.getMessage()}

                    [JAVA CODE]
                    ${methodCode}"""
                )

            } catch (ParseProblemException ex2) {
                println "Error when tryng extract method from code (second try):\n${ex2.getMessage()}"
                methodCode = handlePrompt(projectContext, """
                    There's something wrong with the [JAVA CODE] syntax below. 
                    Based on the [EXCEPTION MESSAGE], [EXCEPTION STACKTRACE] and on your JAVA skills, please fix that, and return only ${methodName} method code for me!
                    [EXCEPTION MESSAGE]
                    ${ex2.getMessage()}

                    [EXCEPTION STACKTRACE]
                    ${Utils.getStackTraceAsString(ex2)}

                    [JAVA CODE]
                    ${methodCode}"""
                )
            }
        }

        return methodCode
    }


    def extractMethodsFromCode(String methodCode){

        def codeList = JavaUtils.extractMethodsFromCode(methodCode, null) as Map<String, String>
        return codeList

    }


    def saveMethods(String program, String mainMethodName, Map<String, String> methods, Map<String, Object> projectContext){

        def project = projectContext.project as Map<String, Object>
        methods.each {methodName, methodCode ->

            def newName = methodName
            if (methodName != mainMethodName){
                newName = "aux" + mainMethodName + methodName
            }

            Utils.anyCollectionSet(project, 'NewMethods.'+program+'.'+newName, methodCode)
        }

    }
}