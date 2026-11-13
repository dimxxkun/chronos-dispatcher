package com.chronos.dispatcher;

import com.chronos.dispatcher.engine.JobDispatcher;
import com.chronos.dispatcher.model.Job;
import com.chronos.dispatcher.model.JobState;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JobDispatcherTest {
    public static void main(String[] args) throws InterruptedException {
        testIdempotencyDeduplication();
        testRetryAndDeadLetterQueue();
        System.out.println("All Chronos-Dispatcher backend verifiers passed successfully!");
    }

    private static void testIdempotencyDeduplication() {
        JobDispatcher dispatcher = new JobDispatcher(2);
        Job job1 = new Job("j1", "idem-tx-1234", 0, 1, j -> {});
        Job job2 = new Job("j2", "idem-tx-1234", 0, 1, j -> {}); // Duplicate key

        boolean submitted1 = dispatcher.submit(job1);
        boolean submitted2 = dispatcher.submit(job2);

        if (!submitted1 || submitted2) {
            throw new AssertionError("Idempotency test failed! Duplicate was not rejected.");
        }
        dispatcher.shutdown();
    }

    private static void testRetryAndDeadLetterQueue() throws InterruptedException {
        JobDispatcher dispatcher = new JobDispatcher(2);
        AtomicInteger attempts = new AtomicInteger(0);

        Job poisonJob = new Job("j-poison", "idem-fail", 0, 2, j -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Simulated persistent database connectivity failure");
        });

        dispatcher.submit(poisonJob);

        // Await until DLQ receives the quarantined job
        long deadline = System.currentTimeMillis() + 2000;
        while (dispatcher.getDlqDepth() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        if (attempts.get() != 2 || dispatcher.getDlqDepth() != 1) {
            throw new AssertionError("Retry/DLQ test failed! DLQ depth: " + dispatcher.getDlqDepth() + ", attempts: " + attempts.get());
        }

        Job dlqJob = dispatcher.getDeadLetterQueue().get(0);
        if (dlqJob.getState() != JobState.DEAD_LETTER) {
            throw new AssertionError("Expected job in DEAD_LETTER state, got: " + dlqJob.getState());
        }

        dispatcher.shutdown();
    }
}
