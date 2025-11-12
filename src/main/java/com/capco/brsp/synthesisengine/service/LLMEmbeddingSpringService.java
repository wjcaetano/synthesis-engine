package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.dto.AgentEmbConfigDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingOptions;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingOptions;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Service(value = "llmEmbeddingSpringService")
public class LLMEmbeddingSpringService {

    private final ContextService contextService;
    private final Map<String, EmbeddingModel> chatModels;


    public float[][] promptEmbeddingAsArray(String prompt, AgentEmbConfigDto config) {
        EmbeddingOptions options = getEmbeddingOptionsForProvider(config);
        EmbeddingModel embeddingModel = getEmbeddingModelForProvider(config.getProvider());

        EmbeddingRequest request = new EmbeddingRequest(List.of(prompt), options);

        log.info("Executing prompt with provider: {}\n Prompt: {}", config.getProvider(), prompt);

        var embeddingResponse = embeddingModel.call(request);

        return embeddingResponse.getResults().stream()
                .map(Embedding::getOutput)
                .toArray(float[][]::new);

    }

    public Map<String, Object> compareFilesSimilarity(List<String> fileNames, List<String> contents, AgentEmbConfigDto config) {
        float[][] embeddings = new float[contents.size()][];
        for (int i = 0; i < contents.size(); i++) {
            embeddings[i] = promptEmbeddingAsArray(contents.get(i), config)[0];
        }

        double[][] similarity = new double[contents.size()][contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            for (int j = 0; j < contents.size(); j++) {
                similarity[i][j] = cosineSimilarity(embeddings[i], embeddings[j]);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileNames", fileNames);
        result.put("similarityMatrix", similarity);
        return result;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private EmbeddingModel getEmbeddingModelForProvider(String llmProvider) {
        String beanName = switch (llmProvider.toLowerCase()) {
            case "azure-openai" -> "azureOpenAiEmbeddingModel";
            case "bedrock-titan" -> "titanEmbeddingModel";
            case "bedrock-cohere" -> "cohereEmbeddingModel";
            default -> throw new IllegalArgumentException("Unsupported LLM provider: " + llmProvider);
        };
        EmbeddingModel model = chatModels.get(beanName);
        if (model == null) {
            throw new IllegalArgumentException("No chat model bean found for provider '" + llmProvider + "'");
        }

        return model;
    }

    private EmbeddingOptions getEmbeddingOptionsForProvider(AgentEmbConfigDto config) {
        if (config == null) {
            throw new IllegalArgumentException("LLM config cannot be null");
        }

        return switch (config.getProvider().toLowerCase()) {
            case "azure-openai" -> buildAzureOptions(config);
            case "bedrock-titan" -> buildBedrockTitanOptions(config);
            case "bedrock-cohere" -> buildBedrockCohereOptions(config);
            default ->
                    throw new IllegalArgumentException("Cannot create embedding options for provider: " + config.getProvider());
        };
    }

    private EmbeddingOptions buildBedrockCohereOptions(AgentEmbConfigDto config) {

        return BedrockCohereEmbeddingOptions.builder()
                .inputType(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType.valueOf(config.getInputType()))
                .build();
    }

    private EmbeddingOptions buildAzureOptions(AgentEmbConfigDto config) {
        return AzureOpenAiEmbeddingOptions.builder()
                .user(config.getUser())
                .deploymentName(config.getDeploymentName())
                .dimensions(config.getDimensions())
                .inputType(config.getInputType())
                .dimensions(config.getDimensions())
                .build();
    }

    private EmbeddingOptions buildBedrockTitanOptions(AgentEmbConfigDto config) {
        return BedrockTitanEmbeddingOptions.builder()
                .inputType(BedrockTitanEmbeddingModel.InputType.valueOf(config.getInputType()))
                .build();
    }
}


