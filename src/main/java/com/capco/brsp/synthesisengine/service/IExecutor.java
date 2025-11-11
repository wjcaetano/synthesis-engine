package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.dto.TaskMap;
import com.capco.brsp.synthesisengine.dto.TransformDto;
import com.capco.brsp.synthesisengine.flow.Flow;
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IExecutor {
    default Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        throw new UnsupportedOperationException();
    }

    default Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        throw new UnsupportedOperationException();
    }

    default List<TaskMap> prepareListOfTasks(ApplicationContext applicationContext, String flowKey) {
        throw new UnsupportedOperationException();
    }

    default Flow prepareFlow(ApplicationContext applicationContext, UUID projectUUID, Integer contentHash, String contextKey) {
        throw new UnsupportedOperationException();
    }

    default Object transform() {
        return null;
    }

    default void eventBeforeAll(ScriptService scriptService, ConcurrentLinkedHashMap<String, Object> projectContext, String eventScript) {
    }

    default void eventAfterAll(ScriptService scriptService, ConcurrentLinkedHashMap<String, Object> projectContext, String eventScript) {
    }

    default void eventBeforeEachMicroservice(ScriptService scriptService, ConcurrentLinkedHashMap<String, Object> projectContext, String eventScript) {
    }

    default void eventAfterEachMicroservice(ScriptService scriptService, ConcurrentLinkedHashMap<String, Object> projectContext, String eventScript) {
    }

    default void eventBeforeEachFile(ScriptService scriptService, ConcurrentLinkedHashMap<String, Object> projectContext, String eventScript, List<TransformDto> history) {
    }

    default void eventAfterEachFile(ScriptService scriptService, ConcurrentLinkedHashMap<String, Object> projectContext, String eventScript, String input, List<TransformDto> history) {
    }
}
