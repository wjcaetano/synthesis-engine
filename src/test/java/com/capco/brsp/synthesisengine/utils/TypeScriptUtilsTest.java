package com.capco.brsp.synthesisengine.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptUtilsTest {

    @Test
    void testEscapeJsString() {
        String input = "Line\nBreak";
        String escaped = TypeScriptUtils.escapeJsString(input);
        assertEquals("Line\\nBreak", escaped);
    }

    @Test
    void testGenerateVariableWithTypeInference() {
        String result = TypeScriptUtils.generateVariable("user", Map.of("id", 1), true);
        assertTrue(result.startsWith("const user: any ="));
        assertTrue(result.contains("\"id\":1"));
    }

    @Test
    void testGenerateFunctionWithTypedArgs() {
        Map<String, Object> args = Map.of("name", "Alice", "age", 30);
        String body = "console.log(name);";
        String func = TypeScriptUtils.generateFunction("showUser", args, "void", body);
        assertTrue(func.contains("name: string"));
        assertTrue(func.contains("age: number"));
        assertTrue(func.contains("function showUser"));
    }

    @Test
    void testGenerateFunctionCall() {
        String call = TypeScriptUtils.generateFunctionCall("showUser", "Alice", 30);
        assertEquals("showUser(\"Alice\", 30);", call);
    }

    @Test
    void testIsValidIdentifier() {
        assertTrue(TypeScriptUtils.isValidJavaScriptIdentifier("var1"));
        assertFalse(TypeScriptUtils.isValidJavaScriptIdentifier("1var"));
    }

    @Test
    void testResolveTypeScriptType() {
        assertEquals("string", TypeScriptUtils.resolveTypeScriptType(String.class));
        assertEquals("number", TypeScriptUtils.resolveTypeScriptType(Integer.class));
        assertEquals("boolean", TypeScriptUtils.resolveTypeScriptType(Boolean.class));
        assertEquals("any[]", TypeScriptUtils.resolveTypeScriptType(List.class));
        assertEquals("any", TypeScriptUtils.resolveTypeScriptType(Map.class));
        assertNotSame("{}", TypeScriptUtils.resolveTypeScriptType(Map.class));
    }

    @Test
    void testMinifyTs() {
        String input = "function x() {\n  // test\n  let a = 1;\n}";
        String minified = TypeScriptUtils.minifyTs(input);
        assertEquals("function x(){let a=1;}", minified);
    }

    @Test
    void testWrapInScriptTag() {
        String code = "let a : number = 5;";
        String result = TypeScriptUtils.wrapInScriptTag(code);
        assertTrue(result.contains("<script type=\"module\">"));
        assertTrue(result.contains("let a : number = 5;"));
        assertEquals("<script type=\"module\">\nlet a : number = 5;\n</script>", result);
    }

    @Test
    void testApplyTemplate() {
        String template = "export const name = {{value}};";
        String result = TypeScriptUtils.applyTemplate(template, Map.of("value", "TypeScript"));
        assertEquals("export const name = \"TypeScript\";", result);
    }

    @Test
    void testGenerateTsObject() {
        Map<String, Object> mapOfObject = new LinkedHashMap<>();
        mapOfObject.put("test", "one");
        mapOfObject.put("test2", "two");
        String equals = "{\"test\":\"one\",\"test2\":\"two\"}";
        assertEquals(equals, TypeScriptUtils.generateTsObject(mapOfObject));
    }

    @Test
    void testGenerateCompleteTypeScript() {
        List<String> lines = new ArrayList<>();
        lines.add("import 'react' from 'React'");
        lines.add("export default function App(){");
        lines.add("return (<div> Teste </div>)");
        lines.add("}");

        StringBuilder equals = new StringBuilder("import 'react' from 'React'\n");
        equals.append("export default function App(){\n");
        equals.append("return (<div> Teste </div>)\n");
        equals.append("}");

        assertEquals(TypeScriptUtils.generateCompleteTypeScript(lines), equals.toString());
    }
}
