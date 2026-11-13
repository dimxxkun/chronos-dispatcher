package com.chronos.dispatcher.engine;

import com.chronos.dispatcher.model.Job;
import com.chronos.dispatcher.model.JobState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobDispatcher {
    private final DelayQueue<Job> queue = new DelayQueue<>();
    private final Map<String, Boolean> idempotencyRegistry = new ConcurrentHashMap<>();
    private final List<Job> deadLetterQueue = new CopyOnWriteArrayList<>();
    private final ExecutorService workerPool;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread dispatcherThread;

    public JobDispatcher(int workerCount) {
        this.workerPool = Executors.newFixedThreadPool(workerCount);
        this.dispatcherThread = new Thread(this::dispatchLoop, "chronos-dispatcher-thread");
        this.dispatcherThread.setDaemon(true);
        this.dispatcherThread.start();
    }

    public boolean submit(Job job) {
        if (job.getIdempotencyKey() != null) {
            Boolean existing = idempotencyRegistry.putIfAbsent(job.getIdempotencyKey(), Boolean.TRUE);
            if (existing != null) {
                // Duplicate detected - reject submission
                return false;
            }
        }
        return queue.offer(job);
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                Job job = queue.poll(100, TimeUnit.MILLISECONDS);
                if (job != null && running.get() && !workerPool.isShutdown()) {
                    try {
                        workerPool.submit(() -> processJob(job));
                    } catch (RejectedExecutionException ignored) {
                        // Suppress during graceful shutdown
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processJob(Job job) {
        job.setState(JobState.RUNNING);
        job.incrementAttempt();

        try {
            job.getTask().accept(job);
            job.setState(JobState.COMPLETED);
        } catch (Exception ex) {
            job.setLastError(ex);
            if (job.getAttemptCount() < job.getMaxRetries()) {
                long backoff = 50L * (1L << (job.getAttemptCount() - 1));
                job.reschedule(backoff);
                queue.offer(job);
            } else {
                job.setState(JobState.DEAD_LETTER);
                deadLetterQueue.add(job);
            }
        }
    }

    public int getQueueDepth() { return queue.size(); }
    public int getDlqDepth() { return deadLetterQueue.size(); }
    public List<Job> getDeadLetterQueue() { return List.copyOf(deadLetterQueue); }

    public void shutdown() {
        running.set(false);
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
