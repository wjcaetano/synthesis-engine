package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TransformDto {
    @Builder.Default
    private UUID uuid = UUID.randomUUID();
    private String sentence;
    private String name;
    private String command;
    private String originalTransformParams;
    private List<Object> parameters;
    private Object content;
    private boolean update;
    @Builder.Default
    private long millisSpent = 0;
    private String cacheHash;
    private List<TransformDto> history;
    @Builder.Default
    private List<String> caches = new ConcurrentLinkedList<>();


    public TransformDto deepClone() {
        return TransformDto.builder()
                .uuid(uuid)
                .sentence(sentence)
                .name(name)
                .command(command)
                .originalTransformParams(originalTransformParams)
                .parameters(parameters == null ? null : new ConcurrentLinkedList<>(parameters))
                .content(content)
                .update(update)
                .millisSpent(millisSpent)
                .cacheHash(cacheHash)
                .history(history == null ? null : history.stream().map(TransformDto::deepClone).toList())
                .caches(caches == null ? new ConcurrentLinkedList<>() : new ConcurrentLinkedList<>(caches))
                .build();
    }

}
