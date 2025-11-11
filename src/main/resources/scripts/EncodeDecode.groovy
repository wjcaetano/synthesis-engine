import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService2
import com.capco.brsp.synthesisengine.service.SuperService2
import com.capco.brsp.synthesisengine.utils.*
import org.springframework.context.ApplicationContext

class EncodeDecode implements IExecutor {
    SuperService2 superService = null
    ScriptService2 scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService2.class)
        this.scriptService = applicationContext.getBean(ScriptService2.class)

        def text = projectContext['$api'].configs.options.fromFile ? Utils.decodeBase64ToString(projectContext['$api'].configs.options.input as String) : projectContext['$api'].configs.options.text as String
        def pass = projectContext['$api'].configs.options.pass as String
        def direction = projectContext['$api'].configs.options.direction as String

        def key = pass.getBytes('UTF-8')

        def xorBytes = { byte[] inb ->
            byte[] out = new byte[inb.length]
            for (int i = 0; i < inb.length; i++) {
                out[i] = (byte) (inb[i] ^ key[i % key.length])
            }
            out
        }

        if (direction == "ENCODE") {
            byte[] obf = xorBytes(text.getBytes('UTF-8'))
            return obf.encodeBase64().toString()
        } else { // "DECODE"
            byte[] obf = text.decodeBase64()       // 'text' holds the Base64 input
            byte[] plain = xorBytes(obf)
            return new String(plain, 'UTF-8')
        }
    }
}