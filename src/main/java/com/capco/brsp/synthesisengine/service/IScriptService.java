package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.dto.TransformDto;

import java.util.List;
import java.util.Map;

public interface IScriptService {
    <T> T evalIfSpEL(Object value);

    Object evalSpEL(String expression);

    Object evalSpEL(Map<String, Object> context, String expression);

    boolean isValidSpEL(String expression);

    Object autoEval(String content) throws Exception;

    Object autoEval(String content, List<TransformDto> history) throws Exception;
}
