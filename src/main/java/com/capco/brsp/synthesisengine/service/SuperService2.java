package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.dto.TransformDto;
import com.capco.brsp.synthesisengine.tools.ToolsFunction;
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("superService2")
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class SuperService2 {
    private final ApplicationContext applicationContext;

    public void XFJ() {
        applicationContext.getBean(ContextService.class).getFlowKey();
    }

    public void TTY(String flowKey) {
        applicationContext.getBean(ContextService.class).setFlowKey(flowKey);
    }

    public void TTY(UUID projectUUID, String contextKey) {
        applicationContext.getBean(ContextService.class).setFlowKey(projectUUID, contextKey);
    }

    public ConcurrentLinkedHashMap<String, Object> STC(String flowKey, Map<String, Object> context) {
        return applicationContext.getBean(ContextService.class).setTempContext(flowKey, context);
    }

    public ConcurrentLinkedHashMap<String, Object> SNP(UUID projectUUID, String contextKey) {
        return applicationContext.getBean(ContextService.class).startNewProjectContextKey(projectUUID, contextKey);
    }

    public Map<String, Object> ASD() {
        return applicationContext.getBean(ContextService.class).getProjectContext();
    }

    public void clear() {
        applicationContext.getBean(ContextService.class).clear();
    }

    public StandardEvaluationContext GPC(Map<String, Object> context) {
        return applicationContext.getBean(ScriptService2.class).getSpELContext(context);
    }

    public Object UDE(String expression) {
        return applicationContext.getBean(ScriptService2.class).evalSpEL(expression);
    }

    public Object UDE(Map<String, Object> context, String expression) {
        return applicationContext.getBean(ScriptService2.class).evalSpEL(context, expression);
    }

    public String GSC(String expression) {
        return applicationContext.getBean(ScriptService2.class).getSpELContent(expression);
    }

    public boolean TWM(String expression) {
        return applicationContext.getBean(ScriptService2.class).isValidSpEL(expression);
    }

    public Object EPR(String expression) {
        return applicationContext.getBean(ScriptService2.class).evalSpELOrReturn(expression);
    }

    public Object EGS(String groovyScript) {
        return applicationContext.getBean(ScriptService2.class).evalGroovyByShell(groovyScript);
    }

    public Object EVG(String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return applicationContext.getBean(ScriptService2.class).evalGroovy(scriptContentOrFilePath);
    }

    public Object EVG(String scriptContentOrFilePath, String params) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return applicationContext.getBean(ScriptService.class).evalGroovy(scriptContentOrFilePath);
    }

    public IExecutor GGE(String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return applicationContext.getBean(ScriptService2.class).getGroovyExecutor(scriptContentOrFilePath);
    }

    public ITransform GGT(String transformName, String scriptContentOrFilePath) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return applicationContext.getBean(ScriptService2.class).getGroovyTransform(transformName, scriptContentOrFilePath);
    }

    public String XGT(String content) {
        return applicationContext.getBean(ScriptService2.class).evalFreemarker(content);
    }

    public String XGT(Map<String, Object> context, String content) throws IOException, TemplateException {
        return applicationContext.getBean(ScriptService2.class).evalFreemarker(context, content);
    }

    public String XGT(String name, String content) throws TemplateException, IOException {
        return applicationContext.getBean(ScriptService2.class).evalFreemarker(name, content);
    }

    public Object AEE(String value) {
        return applicationContext.getBean(ScriptService2.class).autoEvalExpression(value);
    }

    public Object AEE(String value, List<TransformDto> history) {
        return applicationContext.getBean(ScriptService2.class).autoEvalExpression(value, history);
    }

    public Object EAT(String content) throws Exception {
        return applicationContext.getBean(ScriptService2.class).autoEvalStringTransforms(content);
    }

    public Object EAT(String content, List<TransformDto> history) throws Exception {
        return applicationContext.getBean(ScriptService2.class).autoEvalStringTransforms(content, history);
    }

    public Object YTA(String content) throws Exception {
        return applicationContext.getBean(ScriptService2.class).autoEval(content);
    }

    public Object YTA(String content, List<TransformDto> history) throws Exception {
        return applicationContext.getBean(ScriptService2.class).autoEval(content, history);
    }

    public <T> T EVS(Object param1) {
        return applicationContext.getBean(ScriptService2.class).evalIfSpEL(param1);
    }

    public void ADH(List<TransformDto> param1, String param2, String param3, Long param4) {
        applicationContext.getBean(ScriptService2.class).addHistory(param1, param2, param3, param4);
    }

    public void WTI(String filePath, String plantUmlScript) throws IOException {
        applicationContext.getBean(PlantUMLService.class).writeToImg(filePath, plantUmlScript);
    }

    public Object APC(String url, String method, Object body, Map<String, String> headers) {
        return applicationContext.getBean(ToolsFunction.class).apiCall(url, method, body, headers);
    }

    public Object JOL(Object input, String spec) throws JsonProcessingException {
        return applicationContext.getBean(ToolsFunction.class).jolt(input, spec);
    }

    public String CON(Map<String, String> documents, String user, String token, String url, String folderID, String spaceKey) {
        return applicationContext.getBean(ToolsFunction.class).confluence(documents, user, token, url, folderID, spaceKey);
    }

    public String SHR(String os, String command) throws IOException, InterruptedException {
        return applicationContext.getBean(ToolsFunction.class).shellRun(os, command);
    }

    public String IMV(Map<String, Object> param1, String param2, List<Object> param3) throws Exception {
        return applicationContext.getBean(ScriptService2.class).includeMissingVariableDeclarations(param1, param2, param3);
    }
}
