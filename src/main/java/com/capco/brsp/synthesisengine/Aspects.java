package com.capco.brsp.synthesisengine;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "configs.log", havingValue = "FULL")
public class Aspects {

    @Around("execution(* com.capco.brsp.synthesisengine.service.*.*(..))")
    public Object logException(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Exception ex) {
            String methodName = joinPoint.getSignature().getName();

            Object[] args = joinPoint.getArgs();
            StringBuilder argsString = new StringBuilder();

            for (Object arg : args) {
                argsString.append(arg).append("\n");
            }

            log.error("Exception in method: {} with arguments: [\n{}\n]", methodName, argsString, ex);
            throw ex;
        }
    }

    @Around("@annotation(org.springframework.shell.standard.ShellMethod)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();

        var commandName = joinPoint.getSignature().getName();
        Object result = joinPoint.proceed();

        Instant end = Instant.now();
        long timeSpentMillis = Duration.between(start, end).toMillis();

        String formattedTime = formatDuration(timeSpentMillis);
        log.info("Time spent to process the '{}' shell command: {}", commandName, formattedTime);

        return result;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
