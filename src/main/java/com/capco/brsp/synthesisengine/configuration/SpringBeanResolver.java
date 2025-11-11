package com.capco.brsp.synthesisengine.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringBeanResolver implements BeanResolver {
    private final ApplicationContext applicationContext;

    public SpringBeanResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object resolve(EvaluationContext context, String beanName) {
        return applicationContext.getBean(beanName);
    }
}
