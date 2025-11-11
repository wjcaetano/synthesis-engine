package com.capco.brsp.synthesisengine.controller;

import com.capco.brsp.synthesisengine.dto.EvaluateRequestDto;
import com.capco.brsp.synthesisengine.dto.EvaluateResponseDto;
import com.capco.brsp.synthesisengine.service.SynthesisEngineService;
import com.capco.brsp.synthesisengine.service.ContextService;
import com.capco.brsp.synthesisengine.utils.JsonUtils;
import com.capco.brsp.synthesisengine.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolsController {
    @Qualifier("synthesisEngineService")
    private final SynthesisEngineService synthesisEngineService;
    private final ContextService contextService;

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateResponseDto> evaluate(@RequestParam(value = "contextKey", defaultValue = "evaluate") String contextKey, @RequestBody EvaluateRequestDto evaluateRequestDto) {
        try {
            UUID tempUUID = UUID.randomUUID();

            var flowKey = Utils.combinedKey(tempUUID, contextKey);
            contextService.setTempContext(flowKey, evaluateRequestDto.getContext());
            var projectContext = contextService.getProjectContext();

            projectContext.put("projectUUID", tempUUID);
            projectContext.put("llmProjectUUID", evaluateRequestDto.getLlmProjectUUID());

            var evaluateResponseString = synthesisEngineService.evaluate(evaluateRequestDto.getType(), projectContext, evaluateRequestDto.getExpression());

            var projectContextString = JsonUtils.writeAsJsonString(contextService.getProjectContext(), true);
            var evaluateResponseDto = EvaluateResponseDto.builder()
                    .error(false)
                    .errorMessage(null)
                    .errorStack(null)
                    .value(evaluateResponseString)
                    .context(projectContextString)
                    .build();

            return ResponseEntity.ok(evaluateResponseDto);
        } catch (Exception ex) {
            ex.printStackTrace();

            var projectContextString = JsonUtils.writeAsJsonString(contextService.getProjectContext(), true);

            var evaluateResponseDto = EvaluateResponseDto.builder()
                    .error(true)
                    .errorMessage(ex.getMessage())
                    .errorStack(Utils.getStackTraceAsString(ex))
                    .value(null)
                    .context(projectContextString)
                    .build();

            return ResponseEntity.internalServerError().body(evaluateResponseDto);
        } finally {
            contextService.clear();
        }
    }
}
