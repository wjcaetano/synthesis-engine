package com.capco.brsp.synthesisengine.flow;

import com.capco.brsp.synthesisengine.utils.FileUtils;
import com.capco.brsp.synthesisengine.utils.JsonUtils;
import com.capco.brsp.synthesisengine.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.capco.brsp.synthesisengine.utils.FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH;

@Data
@Builder
@Slf4j
public class Flow {
    @JsonIgnore
    private volatile Thread executingThread;

    public static final Map<String, Flow> PROJECT_FLOW = new ConcurrentHashMap<>();

    private final UUID uuid = UUID.randomUUID();
    private final UUID projectUUID;
    private final String contextKey;
    private final String name;
    private final String startMessage;
    private final String endMessage;
    private Date startedAt;
    private Date finishedAt;
    private EnumTaskStatus status;
    private String endpoint;
    private final Queue<Task> tasks;
    private final Queue<Task> polledTasks = new ConcurrentLinkedQueue<>();
    private final Integer totalWeight;
    private String lastMessage;
    private Task currentTask;
    private int contentHash;
    private Map<String, Object> projectContext;

    public void stop() {
        if (this.status == EnumTaskStatus.RUNNING) {
            this.status = EnumTaskStatus.INTERRUPTED;
            if (executingThread != null) executingThread.interrupt();
        }

        saveProjectContext();
    }

    public void executeAsync() {
        CompletableFuture.runAsync(() -> {
            log.info("Starting the Flow '{}' named as '{}' from the endpoint '{}'", uuid, name, endpoint);

            try {
                this.execute();
                log.info("Finished SUCCESSFULLY in '{}' the Flow '{}' named as '{}' from the endpoint '{}'", getTimeSpent(), uuid, name, endpoint);
            } catch (Exception ex) {
                this.setLastMessage(ex.getMessage());
                this.setStatus(EnumTaskStatus.ERROR);
                ex.printStackTrace();
                log.info("Finished UNSUCCESFULLY in '{}' the Flow '{}' named as '{}' from the endpoint '{}'", getTimeSpent(), uuid, name, endpoint);
            }
        });
    }

    private void saveProjectContext() {
        var path = FileUtils.absolutePathJoin(USER_TEMP_PROJECTS_FOLDER_PATH, getFlowKey(), "meta", "projectContext");
        String projectContextString = JsonUtils.writeAsJsonStringCircular(this.getProjectContext(), true, false);
        FileUtils.writeFile(path, projectContextString, true);
    }

    private boolean isTasksPending() {
        return !this.tasks.isEmpty() || (this.currentTask != null && this.currentTask.getStatus() == EnumTaskStatus.NEW);
    }

    private void execute() {
        executingThread = Thread.currentThread();

        if (!isTasksPending()) {
            return;
        }

        this.setStartedAt(new Date());
        this.setStatus(EnumTaskStatus.RUNNING);

        lastMessage = startMessage;
        log.info(startMessage);

        while (isTasksPending()) {
            if (Thread.currentThread().isInterrupted()) {
                this.status = EnumTaskStatus.INTERRUPTED;
                log.info("Flow '{}' interrupted", name);
                saveProjectContext();
                return;
            }

            try {
                if (status != EnumTaskStatus.INTERRUPTED) {
                    Task task = currentTask != null && currentTask.getStatus() == EnumTaskStatus.NEW ? currentTask : poll();
                    this.setCurrentTask(task);

                    lastMessage = task.getStartMessage();
                    task.execute("[" + polledTasks.size() + "/" + (tasks.size() + polledTasks.size()) + "]");
                    lastMessage = task.getEndMessage();
                } else {
                    return;
                }
            } catch (Exception ex) {
                setFinishedAt(new Date());
                setStatus(EnumTaskStatus.ERROR);
                lastMessage = ex.getMessage();
                saveProjectContext();
                throw ex;
            }
        }

        lastMessage = endMessage;
        log.info(endMessage);

        this.setFinishedAt(new Date());
        this.setStatus(EnumTaskStatus.COMPLETE);

        saveProjectContext();
    }

    private List<Task> getTasksDone() {
        return polledTasks.stream().filter(it -> it.getStatus().equals(EnumTaskStatus.COMPLETE)).toList();
    }

    public int getTotalTasks() {
        return polledTasks.size() + tasks.size();
    }

    public int getTotalTasksDone() {
        return getTasksDone().size();
    }

    public BigDecimal getWeigthProgress() {
        BigDecimal weightDone = BigDecimal.valueOf(getTasksDone().stream().mapToInt(Task::getWeight).sum());
        if (totalWeight <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN);
        }
        return weightDone.divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_DOWN).multiply(new BigDecimal(100));
    }

    private Task poll() {
        Task task = this.tasks.poll();
        polledTasks.add(task);

        return task;
    }

    public String getTimeSpent() {
        return Utils.diffBetweenDates(startedAt, Utils.nvl(finishedAt, new Date()));
    }

    // TODO: Review to rely on a more robust solution
    public String getFlowKey() {
        return Utils.combinedKey(this.projectUUID, this.contextKey);
    }
}
