package com.capco.brsp.synthesisengine.tools;

import com.capco.brsp.synthesisengine.service.ScriptService;
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList;
import com.capco.brsp.synthesisengine.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ToolsService {
    @Autowired
    private ToolsFunction toolsFunction;

    @Autowired
    private ScriptService scriptService;

    public List<ToolItem> parseChain(String chainScript) throws JsonProcessingException {
        return JsonUtils.readAsListOf(chainScript, ToolItem.class);
    }

    public List<Object> chainExecute(Map<String, Object> context, List<ToolItem> chain) throws InvocationTargetException, IllegalAccessException, JsonProcessingException {
        List<Object> chainResults = new ConcurrentLinkedList<>();
        context.put("results", chainResults);

        for (ToolItem toolItem : chain) {
            var toolMethod = getToolMethod(toolItem);

            List<Object> orderedCallParameters = new ConcurrentLinkedList<>();
            var parameterNames = Arrays.stream(toolMethod.getParameters()).map(it -> it.getAnnotation(ToolParameter.class).name()).toList();
            for (String parameterName : parameterNames) {
                Object parameterValue = toolItem.getParameters().get(parameterName);
                if (parameterValue instanceof String parameterStringValue && scriptService.isValidSpEL(parameterStringValue)) {
                    parameterValue = scriptService.evalSpEL(context, parameterStringValue);
                }

                orderedCallParameters.add(parameterValue);
            }

            Object result = toolMethod.invoke(toolsFunction, orderedCallParameters.toArray());

            var outType = toolItem.getOutType();
            if (result != null && outType != null) {
                Class<?> outTypeClass = switch (outType) {
                    case "string" -> String.class;
                    case "decimal" -> BigDecimal.class;
                    case "integer" -> Integer.class;
                    case "map" -> Map.class;
                    case "list" -> List.class;
                    default -> throw new IllegalStateException("Unexpected value: " + toolItem.getOutType());
                };

                if (outTypeClass == String.class) {
                    result = JsonUtils.writeAsJsonString(result, true);
                } else if (!outTypeClass.isAssignableFrom(result.getClass())) {
                    String resultString = result instanceof String ? (String) result : JsonUtils.writeAsJsonString(result, true);
                    result = JsonUtils.readAs(resultString, outTypeClass);
                }
            }

            chainResults.add(result);
        }

        return chainResults;
    }

    public Method getToolMethod(ToolItem toolItem) {
        var name = toolItem.getTool();
        var parameterKeys = toolItem.getParameters().keySet();

        var eligibleMethodsByName = Arrays.stream(ToolsFunction.class.getDeclaredMethods()).filter(it -> it.isAnnotationPresent(ToolName.class) && Objects.equals(it.getAnnotation(ToolName.class).name(), name)).toList();

        if (eligibleMethodsByName.isEmpty()) {
            throw new IllegalArgumentException("None method match the tool name: " + name);
        }

        var eligibleMethodsByNameAndParams = eligibleMethodsByName.stream().filter(it -> {
            var allParameters = Arrays.stream(it.getParameters()).map(it2 -> it2.getAnnotation(ToolParameter.class).name()).toList();
            var requiredParameters = Arrays.stream(it.getParameters()).filter(it2 -> it2.getAnnotation(ToolParameter.class).required()).map(it2 -> it2.getAnnotation(ToolParameter.class).name()).toList();

            return allParameters.containsAll(parameterKeys) && parameterKeys.containsAll(requiredParameters);
        }).toList();

        if (eligibleMethodsByNameAndParams.isEmpty()) {
            throw new IllegalArgumentException("None method match the tool name: " + name + " and params: " + parameterKeys);
        } else if (eligibleMethodsByNameAndParams.size() > 1) {
            throw new IllegalArgumentException("There are more than one method that match the tool name: " + name + " and params: " + parameterKeys);
        }

        return eligibleMethodsByNameAndParams.stream().findFirst().orElse(null);
    }
}
