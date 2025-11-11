import com.capco.brsp.synthesisengine.dto.TaskMap
import com.capco.brsp.synthesisengine.dto.TransformDto
import com.capco.brsp.synthesisengine.service.ContextService
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.FileUtils
import com.capco.brsp.synthesisengine.utils.JavaUtils
import com.capco.brsp.synthesisengine.utils.Utils
import org.springframework.context.ApplicationContext

class AfterAll implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null

    List<TaskMap> prepareListOfTasks(ApplicationContext applicationContext, String flowKey) throws Exception {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)
        def contextService = applicationContext.getBean(ContextService.class)

        Runnable afterAllRunnable = {
            contextService.setFlowKey(flowKey)
            def context = contextService.getProjectContext()

            def rootFolder = context.rootFolder as String

            def listOfFileDtos = FileUtils.crawlDirectory(rootFolder)
            def map = context.files_metadata as Map<String, Map<String, Object>>
            map.forEach((key, value) -> {
                def fileHistory = value.history as List<TransformDto>
                listOfFileDtos.stream().filter(it2 -> it2.getPath().equals(key)).findFirst().ifPresent(fileDto -> fileDto.setHistory(fileHistory));
            })
            for (def f : listOfFileDtos) {
                f.setPath(f.getPath().replace("\\", "/"))
            }

            def listOfJavaFileDtos = listOfFileDtos.findAll { it.path.endsWith(".java") }

            def cobolContext = context['$api'].files.cobolPrograms.cobolPrograms[0].raw_code
            def jclContext = context['$api'].files.cobolPrograms.jclJobs[0].raw_code
            def recipe = context.recipe as Map<String, Object>

            def lastAnswer = null
            while (lastAnswer != "OK") {
                def mainServiceFileDto = listOfJavaFileDtos.find { it.path.endsWith("MainService.java") }

//                def prompt = """
//                        [CONTRAINTS]
//                        You should ALWAYS answer using the YAML template below, naming actions with sequenced numbered labels, each action should have just one set of the fields specified on the template:
//
//                        [YAML_TEMPLATE]
//                        action1:
//                            file: path of the file you wanna change
//                            class: one of two options (null for content outside of any class or the class name)
//                            memberType: null for class itself, or one of the com.github.JavaParser node_types between: FieldDeclaration, MethodDeclaration, ConstructorDeclaration, ClassOrInterfaceDeclaration
//                            identifier: name of a class, method, field, constructor, nested class, ...
//                            operation: one of the three options (INCLUDE|REMOVE|REPLACE)
//                            content: |
//                                multiline text of the content you wanna INCLUDE or REPLACE (it will be null if the operation is to REMOVE)
//
//                        NEVER include anything else, just the YAML_TEMPLATE filled with meaningful values following the tasks you are asked for to apply!
//
//                        IF there's nothing else to be fixed, just retrieve a single word STOP (exactly just these two characters)
//
//                        [TASK]
//                        Have a look at the content below and fix anything wrong with syntax. It is from the file: ${mainServiceFileDto.path}
//
//                        ${mainServiceFileDto.content}
//                        """.toString()


                  def String prefixLines = { String input ->
                      String[] lines = input.split("\n");
                      StringBuilder result = new StringBuilder();

                      for (int i = 0; i < lines.length; i++) {
                          result.append(String.format("%04d|%s%n", i + 1, lines[i]));
                      }

                      return result.toString();
                  }

                def fileContent = prefixLines(mainServiceFileDto.content)
                def prompt = """
[RESPONSE CONSTRAINTS]
You are a code assistant suggesting safe and verifiable changes to Java source files.

Produce a YAML document where each top-level key (e.g., `action1`, `action2`, etc.) defines a single code change operation. These actions will be executed in the order they appear.

Each action must include the following fields:

- `targetPath`: /monolith/src/main/java/com/capco/monolith/service/MainService.java

- `firstLine`: integer value of the line where the action should start (count starting from 1)
- `lasttLine`: integer value of the line where the action should stop (with this line included on the operation)

  Example:

  - `action`: One of:
    - `INCLUDE` (to insert new content),
    - `REMOVE` (to delete existing content),
    - `REPLACE` (to replace an existing block with new content)

- `content`: The code to include or replace with (multi-line string block). Leave it empty or omit if the action is `REMOVE`.

- `reason`: A short explanation (max 50 words) of **why** this change is proposed.

### ✅ YAML Example:

```yaml
action1:
  targetPath: /project/src/main/service/MyService.java
  firstLine: 85
  lastLine: 85
  action: REPLACE
  content: |
    return userRepository.findById(id).orElseThrow();
  reason: |
    Improves null safety and simplifies user lookup with optional chaining.

action2:
  targetPath: /project/src/main/service/MyService.java
  firstLine: 10
  lastLine: 15
  action: REMOVE
  content: null
  reason: |
    The field is no longer used in the class and should be removed to reduce clutter.
```

[COBOL CODE]
${cobolContext}

[TASK]
The [FILE CONTENT] below (path: ${mainServiceFileDto.path}) should generate the same result as the [COBOL CONTENT] above.
Take the actions needed to fix any JAVA issues (syntax, integration, etc, ...) and also ensure that the translated code will be able to replace
the COBOL code.
CONSIDER THAT THIS FILE IS THE ONLY ONE THAT EXIST IN THE WHOLE PROJECT AND SHOULD ALWAYS BE THE ONLY ONE,
SO EVERY LOGIC NEEDS TO BE ADDRESSED INSIDE THIS FILE!
IMPORTANT: the line numbers don't exist on the original FILE CONTENT, they are just here to ease your task!

[FILE CONTENT]
${fileContent}
                """.toString()
                lastAnswer = scriptService.handleAgent(context, prompt)
                lastAnswer = Utils.extractMarkdownCode(lastAnswer) as String

                if (lastAnswer.trim() != "OK") {
                    try {
                        JavaUtils.operate(mainServiceFileDto, rootFolder, lastAnswer)
                    } catch (Exception ex) {
                        System.out.println("Failed to process the YamlAction!")
                        System.out.println(Utils.getStackTraceAsString(ex))
                    }
                } else {
                    System.out.println("OK detected!");
                }
            }

            System.out.println("Finishing the refactor after all process!");
        }

        TaskMap afterAllTaskMap = new TaskMap("After ALL Task", afterAllRunnable)

        return List.of(afterAllTaskMap)
    }
}
