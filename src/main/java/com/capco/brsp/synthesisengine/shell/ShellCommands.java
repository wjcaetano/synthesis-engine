package com.capco.brsp.synthesisengine.shell;

import com.capco.brsp.synthesisengine.SynthesisEngineApplication;
import com.capco.brsp.synthesisengine.dto.YamlActionItem;
import com.capco.brsp.synthesisengine.service.ContextService;
import com.capco.brsp.synthesisengine.service.ScriptService;
import com.capco.brsp.synthesisengine.service.SynthesisEngineService;
import com.capco.brsp.synthesisengine.tools.ToolsFunction;
import com.capco.brsp.synthesisengine.tools.ToolsService;
import com.capco.brsp.synthesisengine.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.util.FileUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Slf4j
@ShellComponent
@RequiredArgsConstructor
@Profile("dev")
public class ShellCommands {
    private static final Path EXECUTORS_PATH;
    private static final Path RECIPES_PATH;

    static {
        try {
            EXECUTORS_PATH = Paths.get(SynthesisEngineApplication.class.getClassLoader().getResource("executors").toURI());
            RECIPES_PATH = Paths.get(SynthesisEngineApplication.class.getClassLoader().getResource("recipes").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final ContextService contextService;

    @Qualifier("scriptService")
    private final ScriptService scriptService;
    private final ApplicationContext applicationContext;

    private final UUID projectUUID = UUID.fromString("07d8b755-61c6-4379-8615-4de06797751a");

    private final ToolsFunction toolsFunction;
    private final ToolsService toolsService;
    private final SynthesisEngineService synthesisEngineService;

    private final Path testResourcesSamplesFolder = FileUtils.pathJoin(System.getProperty("user.dir"), "src", "test", "resources", "samples");

    @ShellMethod(key = "run")
    public void run(
            @ShellOption(value = {"projectUUID", "p"}) UUID projectUUID,
            @ShellOption(value = {"contextKey", "c"}, defaultValue = "default") String contextKey,
            @ShellOption(value = {"recipeRef", "r"}, defaultValue = "mock.yaml") String recipeRef
    ) throws Exception {
        String recipeContent = "recipe: " + recipeRef;
        Map<String, Object> configs = new ConcurrentLinkedHashMap<>();
        Map<String, String> files = new ConcurrentLinkedHashMap<>();

        var recipe = synthesisEngineService.readRecipe(recipeContent);
        if ("ProjectModelExecutor3.java".equalsIgnoreCase((String) recipe.get("executor"))) {
            synthesisEngineService.code2(projectUUID, contextKey, recipeContent, recipe, configs, files);
        } else {
            synthesisEngineService.code(projectUUID, contextKey, recipeContent, recipe, configs, files);
        }
    }

    @ShellMethod(key = "java")
    public void java() throws Exception {
        File llmResponseFile = new File("C:\\projects\\legacy-modernization-toolkit\\brsp-synthesis-engine\\src\\test\\resources\\JavaUtils\\LLMRequestByLine.md");
        if (!llmResponseFile.exists()) {
            throw new FileNotFoundException("Didn't found the recipe file!");
        }
        String llmResponseFileContent = FileUtil.readAsString(llmResponseFile);

        llmResponseFileContent = (String) Utils.extractMarkdownCode(llmResponseFileContent);
        Map<String, YamlActionItem> llmResponseYaml = YamlUtils.readYAMLContentAsMap(llmResponseFileContent, YamlActionItem.class);
    }

    @ShellMethod(key = "get-env")
    public void getEnvVariable(@ShellOption(defaultValue = ShellOption.NULL) String envVariableName) {
        var envVariableValue = Utils.getEnvVariable(envVariableName);
        log.info("Env Variable Content:\n{}: {}", envVariableName, envVariableValue);
    }

    @ShellMethod(key = "autoeval")
    public void autoEval(@ShellOption(defaultValue = ShellOption.NULL) String template) throws Exception {
        contextService.startNewProjectContextKey(UUID.randomUUID(), "shell-command");

        var result = scriptService.autoEval(template);

        String finalContent = (result instanceof String resultString) ? resultString : JsonUtils.writeAsJsonString(result, true);

        log.info("Result:\n{}", finalContent);
    }

    @ShellMethod(key = "duckduckgo")
    public void duckDuckGo(@ShellOption String query) throws Exception {
        var r = toolsFunction.duckDuckGo(query, 3);
        log.info("\nResult\n{}", r);
    }

//        if (code == null) {
//            File myPython = new File("src/main/resources/scripts/hello-python.py");
//            code = FileUtil.readAsString(myPython);
//        }
//
//        try (Context context = Context.create()) {
//            var result = context.eval("python", code);
//            System.out.println(result);
//        }
}
