package com.capco.brsp.synthesisengine.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaScriptUtilsTest {

    @Test
    void testEscapeJsString() {
        String input = "Hello \"World\"\nTest";
        String escaped = JavaScriptUtils.escapeJsString(input);
        assertEquals("Hello \\\"World\\\"\\nTest", escaped);
    }

    @Test
    void testGenerateVariable() {
        String result = JavaScriptUtils.generateVariable("user", Map.of("name", "John"), true);
        assertTrue(result.startsWith("const user ="));
        assertTrue(result.contains("\"name\":\"John\""));
    }

    @Test
    void testGenerateFunctionCall() {
        String call = JavaScriptUtils.generateFunctionCall("greet", "John", 30);
        assertEquals("greet(\"John\", 30);", call);
    }

    @Test
    void testGenerateFunction() {
        String body = "console.log(name);";
        String func = JavaScriptUtils.generateFunction("printName", new String[]{"name"}, body);
        assertTrue(func.contains("function printName(name)"));
        assertTrue(func.contains("console.log(name);"));
    }

    @Test
    void testApplyTemplate() {
        String template = "Hello, \"{{name}}\"!";
        String result = JavaScriptUtils.applyTemplate(template, Map.of("name", "Alice"));
        assertEquals("Hello, \"Alice\"!", result);
    }

    @Test
    void testIsValidIdentifier() {
        assertTrue(JavaScriptUtils.isValidJavaScriptIdentifier("myVar"));
        assertFalse(JavaScriptUtils.isValidJavaScriptIdentifier("123abc"));
    }

    @Test
    void testWrapInScriptTag() {
        String code = "console.log('Hello');";
        String result = JavaScriptUtils.wrapInScriptTag(code);
        assertTrue(result.startsWith("<script>"));
        assertTrue(result.endsWith("</script>"));
    }

    @Test
    void testMinifyJs() {
        String input = "function test() {\n    // comment\n    let a = 1;\n}";
        String result = JavaScriptUtils.minifyJs(input);
        assertEquals("function test(){let a=1;}", result);
    }

    @Test
    void testGenerateCompleteJavaScript() {
        List<String> lines = new ArrayList<>();
        lines.add("import 'react' from 'React'");
        lines.add("export default function App(){");
        lines.add("return (<div> Teste </div>)");
        lines.add("}");

        StringBuilder equals = new StringBuilder("import 'react' from 'React'\n");
        equals.append("export default function App(){\n");
        equals.append("return (<div> Teste </div>)\n");
        equals.append("}");

        assertEquals(JavaScriptUtils.generateCompleteJavaScript(lines), equals.toString());
    }

    @Test
    void testExtractFunctionAndMethodNames() {
        String code = """
                    function doSomething() {}
                    const sayHello = () => {};
                    let calculate = (x) => x * 2;
                    class User {
                        greet() {
                            console.log("Hi");
                        }
                    }
                """;

        List<String> functions = JavaScriptUtils.extractFunctionAndMethodNames(code);
        List<String> listExpected = new ArrayList<>();
        listExpected.add("doSomething");
        listExpected.add("sayHello");
        listExpected.add("calculate");
        listExpected.add("greet");

        assertEquals(listExpected, functions);
    }

    @Test
    void testExtractFunctionsWithBodiesUsingMap() {
        String code = """
                    function greet() {
                        console.log("Hello");
                    }
                
                    const add = (a, b) => {
                        return a + b;
                    }
                
                    class MathOps {
                        multiply(x, y) {
                            return x * y;
                        }
                
                        square(n) {
                            return n * n;
                        }
                    }
                """;

        List<Map<String, Object>> listOfFunctions = JavaScriptUtils.extractFunctionsWithBodies(code);

        assertEquals(4, listOfFunctions.size());


        for (Map<String, Object> functions : listOfFunctions) {
            assertTrue(functions.values().stream().findFirst().isPresent());
        }
    }
}
