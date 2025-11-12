package com.capco.brsp.synthesisengine.flow;

import com.capco.brsp.synthesisengine.utils.Utils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Slf4j
public class Task {
    private final UUID uuid = UUID.randomUUID();
    private final String name;
    private final String startMessage;
    private final String endMessage;
    private List<String> rotateMessages;
    private Date startedAt;
    private Date finishedAt;
    private EnumTaskStatus status;
    private final Integer weight;
    private final Runnable runnable;

    public void execute(String messagePrefix) {
        this.setStartedAt(new Date());
        this.setStatus(EnumTaskStatus.RUNNING);

        log.info("{} {}", messagePrefix, startMessage);

        try {
            this.runnable.run();
        } catch (Exception ex) {
            setStatus(EnumTaskStatus.ERROR);
            throw ex;
        }

        log.info("{} {}", messagePrefix, endMessage);

        this.setFinishedAt(new Date());
        this.setStatus(EnumTaskStatus.COMPLETE);
    }

    public String getTimeSpent() {
        return Utils.diffBetweenDates(startedAt, Utils.nvl(finishedAt, new Date()));
    }
}