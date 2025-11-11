package com.capco.brsp.synthesisengine.service;

import org.springframework.context.ApplicationContext;

import java.util.Map;

public interface ITransform {
    default String execute(ApplicationContext applicationContext, Map<String, Object> projectContext, String content, String transformParams) {
        return content;
    }
}
