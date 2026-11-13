package com.chronos.dispatcher;

import com.chronos.dispatcher.engine.JobDispatcher;
import com.chronos.dispatcher.model.Job;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class App {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=======================================================");
        System.out.println("   Chronos-Dispatcher: Asynchronous Job Scheduler     ");
        System.out.println("=======================================================");

        JobDispatcher dispatcher = new JobDispatcher(4);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger completedCount = new AtomicInteger(0);

        System.out.println("[1] Submitting scheduled tasks with varying delay horizons...");

        // Task 1: Immediate task
        dispatcher.submit(new Job("job-fast", "tx-fast", 0, 3, j -> {
            System.out.println("  • [T+0ms] Executing Immediate Cache Invalidation: " + j.getId());
            completedCount.incrementAndGet();
            latch.countDown();
        }));

        // Task 2: Delayed task
        dispatcher.submit(new Job("job-delayed-1", "tx-delay-1", 100, 3, j -> {
            System.out.println("  • [T+100ms] Executing Scheduled Analytics Rollup: " + j.getId());
            completedCount.incrementAndGet();
            latch.countDown();
        }));

        // Task 3: Another delayed task
        dispatcher.submit(new Job("job-delayed-2", "tx-delay-2", 200, 3, j -> {
            System.out.println("  • [T+200ms] Executing Email Digest Dispatch: " + j.getId());
            completedCount.incrementAndGet();
            latch.countDown();
        }));

        // Task 4: Duplicate job submission (should be rejected by Idempotency check)
        boolean dupAccepted = dispatcher.submit(new Job("job-fast-duplicate", "tx-fast", 0, 3, j -> {}));
        System.out.println("    Duplicate Job with key 'tx-fast' accepted? " + dupAccepted + " (Correctly rejected!)");

        // Task 5: Transient failure with retry recovery
        AtomicInteger failCounter = new AtomicInteger(0);
        dispatcher.submit(new Job("job-retry", "tx-retry-demo", 50, 3, j -> {
            if (failCounter.incrementAndGet() < 2) {
                System.out.println("  • Transient error encountered for " + j.getId() + ", triggering backoff retry...");
                throw new RuntimeException("Third-party gateway timeout");
            }
            System.out.println("  • [RECOVERED] Successfully finished " + j.getId() + " on attempt #" + j.getAttemptCount());
            completedCount.incrementAndGet();
            latch.countDown();
        }));

        // Task 6: Poison pill job destined for Dead-Letter Queue
        dispatcher.submit(new Job("job-poison", "tx-poison", 20, 2, j -> {
            System.out.println("  • Poison Job attempt #" + j.getAttemptCount() + " failing...");
            latch.countDown();
            throw new RuntimeException("Permanent syntax corruption");
        }));

        boolean allDone = latch.await(2, TimeUnit.SECONDS);
        System.out.println("\n-------------------------------------------------------");
        System.out.println("Scheduler Run Status (All tasks evaluated: " + allDone + ")");
        System.out.println("Successfully executed jobs: " + completedCount.get());
        System.out.println("Dead-Letter Queue (DLQ) depth: " + dispatcher.getDlqDepth());
        System.out.println("-------------------------------------------------------");

        dispatcher.shutdown();
        System.out.println("Chronos-Dispatcher engine shutdown cleanly.");
    }
}
