package com.chronos.dispatcher.model;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Job implements Delayed {
    private final String id;
    private final String idempotencyKey;
    private final Consumer<Job> task;
    private final int maxRetries;
    private long triggerTimeMs;
    private int attemptCount;
    private JobState state;
    private Throwable lastError;

    public Job(String id, String idempotencyKey, long delayMs, int maxRetries, Consumer<Job> task) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.triggerTimeMs = System.currentTimeMillis() + delayMs;
        this.maxRetries = maxRetries;
        this.task = task;
        this.attemptCount = 0;
        this.state = JobState.PENDING;
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Consumer<Job> getTask() { return task; }
    public int getMaxRetries() { return maxRetries; }
    public int getAttemptCount() { return attemptCount; }
    public JobState getState() { return state; }
    public Throwable getLastError() { return lastError; }

    public void incrementAttempt() { this.attemptCount++; }
    public void setState(JobState state) { this.state = state; }
    public void setLastError(Throwable t) { this.lastError = t; }

    public void reschedule(long backoffMs) {
        this.triggerTimeMs = System.currentTimeMillis() + backoffMs;
        this.state = JobState.PENDING;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = triggerTimeMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.triggerTimeMs, ((Job) o).triggerTimeMs);
    }
}
