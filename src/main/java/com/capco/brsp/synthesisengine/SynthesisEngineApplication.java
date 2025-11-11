package com.capco.brsp.synthesisengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy
@SpringBootApplication
public class SynthesisEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynthesisEngineApplication.class, args);
    }

}
