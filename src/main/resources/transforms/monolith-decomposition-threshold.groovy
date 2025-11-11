import com.capco.brsp.synthesisengine.service.ITransform
import com.capco.brsp.synthesisengine.tools.ToolsFunction
import com.capco.brsp.synthesisengine.utils.JsonUtils
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext

class MonolithDecompositionThreshold implements ITransform {
    def logger = LoggerFactory.getLogger("MonolithDecompositionThreshold")

    private static final String JOLT_SPEC = """
        [
          {
            "operation": "shift",
            "spec": {
              "*": {
                "*": "[&1].&0",
                "paragraph": {
                  "*": {
                    "name": {
                      "EXIT|*EXIT|EXIT*|*EXIT*": null,
                      "*": {
                        "@(2)": "[&5].paragraph"
                      }
                    }
                  }
                }
              }
            }
          },
          {
            "operation": "shift",
            "spec": {
              "*": {
                "paragraph": {
                  "@(1)": "[]"
                }
              }
            }
          }
        ]
    """

    @Override
    String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        ToolsFunction toolsFunction = applicationContext.getBean(ToolsFunction.class)
        String afterJolt = toolsFunction.jolt(content, JOLT_SPEC)

        var parsedAfterJolt = JsonUtils.readAsList(afterJolt)


        return "Failed to execute process the content below:\n\n${content}"
    }
}
