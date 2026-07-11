package com.blinkit.phase1.stress;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates OutOfMemoryError: Unable to create new native thread
 * 
 * This simulates a REAL BUG found in production systems:
 * - Creating a new thread for each notification (no thread pool!)
 * - Not shutting down executor services
 * - Unbounded thread creation under load
 * 
 * Run with: java -Xss256k ThreadOOMDemo
 * (Smaller stack = more threads before OOM, faster demo)
 */
public class ThreadOOMDemo {

    private static final AtomicInteger threadCount = new AtomicInteger(0);
    private static final AtomicInteger notificationsSent = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  OutOfMemoryError: Unable to create new native thread        ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Run with: java -Xss256k ThreadOOMDemo                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        String mode = args.length > 0 ? args[0] : "bug";

        switch (mode) {
            case "bug" -> demonstrateBug_NewThreadPerRequest();
            case "bug2" -> demonstrateBug_UnboundedExecutor();
            case "bug3" -> demonstrateBug_ExecutorNotShutdown();
            case "fix" -> demonstrateFix_BoundedThreadPool();
            default -> {
                System.out.println("Usage: java ThreadOOMDemo [bug|bug2|bug3|fix]");
                System.out.println("  bug  - New thread per request (WILL crash)");
                System.out.println("  bug2 - Unbounded cached thread pool (WILL crash)");
                System.out.println("  bug3 - Executor not shutdown (memory leak)");
                System.out.println("  fix  - Bounded thread pool (CORRECT approach)");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUG 1: Creating a NEW THREAD for each request (Classic Beginner Mistake)
    // ═══════════════════════════════════════════════════════════════════════════
    static void demonstrateBug_NewThreadPerRequest() {
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ BUG 1: New Thread Per Request                                │");
        System.out.println("│                                                              │");
        System.out.println("│ Scenario: Blinkit sends order notification for each order    │");
        System.out.println("│ Bug: Creating new Thread() for each notification             │");
        System.out.println("│ Result: OutOfMemoryError when orders spike (flash sale!)     │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        // ❌ BAD CODE: This is what a junior developer might write
        System.out.println("Simulating flash sale - 10,000 orders coming in...\n");

        try {
            for (int orderId = 1; orderId <= 100_000; orderId++) {
                final int id = orderId;
                
                // ❌ WRONG: Creating a new thread for EACH notification!
                Thread notificationThread = new Thread(() -> {
                    sendOrderNotification(id);
                }, "notification-" + id);
                
                notificationThread.start();
                // Note: We're not even joining/waiting - threads pile up!
                
                int count = threadCount.incrementAndGet();
                
                if (count % 1000 == 0) {
                    System.out.println("Created " + count + " threads, " +
                        "Active threads: " + Thread.activeCount());
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n🔴 CRASHED! " + e.getMessage());
            System.out.println("   Threads created before crash: " + threadCount.get());
            System.out.println("   Notifications actually sent: " + notificationsSent.get());
            System.out.println("\n   This happens during flash sales when orders spike!");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUG 2: Using Executors.newCachedThreadPool() (Unbounded!)
    // ═══════════════════════════════════════════════════════════════════════════
    static void demonstrateBug_UnboundedExecutor() {
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ BUG 2: Unbounded Cached Thread Pool                          │");
        System.out.println("│                                                              │");
        System.out.println("│ Scenario: Using Executors.newCachedThreadPool()              │");
        System.out.println("│ Bug: This creates UNLIMITED threads on demand!               │");
        System.out.println("│ Result: OOM when all threads are busy + new requests come    │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        // ❌ BAD: newCachedThreadPool creates unlimited threads!
        // From Javadoc: "Creates new threads as needed"
        // Under load, this can create thousands of threads
        ExecutorService executor = Executors.newCachedThreadPool();

        System.out.println("Simulating slow notification service (each takes 5 seconds)...");
        System.out.println("Submitting 50,000 tasks to cached thread pool...\n");

        try {
            for (int orderId = 1; orderId <= 50_000; orderId++) {
                final int id = orderId;
                
                executor.submit(() -> {
                    threadCount.incrementAndGet();
                    sendSlowNotification(id); // Takes 5 seconds!
                });
                
                if (orderId % 1000 == 0) {
                    System.out.println("Submitted " + orderId + " tasks, " +
                        "Active threads: " + Thread.activeCount());
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n🔴 CRASHED! " + e.getMessage());
            System.out.println("   Tasks submitted before crash: " + threadCount.get());
            System.out.println("\n   newCachedThreadPool() is DANGEROUS under load!");
        } finally {
            executor.shutdownNow();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUG 3: Creating ExecutorService but never shutting down (Memory Leak)
    // ═══════════════════════════════════════════════════════════════════════════
    static void demonstrateBug_ExecutorNotShutdown() {
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ BUG 3: ExecutorService Never Shutdown                        │");
        System.out.println("│                                                              │");
        System.out.println("│ Scenario: Creating executor in a method without shutdown     │");
        System.out.println("│ Bug: Each API call creates a new executor that never dies    │");
        System.out.println("│ Result: Thread count grows over time → eventual OOM          │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        System.out.println("Simulating multiple API calls, each creating its own executor...\n");

        try {
            for (int apiCall = 1; apiCall <= 10_000; apiCall++) {
                // ❌ BAD: Creating executor inside a method
                processOrderWithLeakyExecutor(apiCall);
                
                if (apiCall % 500 == 0) {
                    System.out.println("API calls: " + apiCall + 
                        ", Active threads: " + Thread.activeCount());
                    
                    // Give some time to see thread accumulation
                    Thread.sleep(100);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n🔴 CRASHED! " + e.getMessage());
            System.out.println("   This is a SLOW memory leak - threads accumulate over hours/days!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ❌ This method has a bug - executor is never shutdown!
    static void processOrderWithLeakyExecutor(int orderId) {
        // BAD: Creating a new executor for each order
        // AND never shutting it down!
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        executor.submit(() -> sendOrderNotification(orderId));
        executor.submit(() -> updateInventory(orderId));
        
        // ❌ MISSING: executor.shutdown()!
        // The executor's threads stay alive forever!
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIX: Bounded Thread Pool with Rejection Handler
    // ═══════════════════════════════════════════════════════════════════════════
    static void demonstrateFix_BoundedThreadPool() throws InterruptedException {
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ FIX: Bounded Thread Pool with Queue                          │");
        System.out.println("│                                                              │");
        System.out.println("│ Solution: Fixed thread pool + bounded queue + rejection      │");
        System.out.println("│ Result: Handles flash sale gracefully, no OOM                │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        // ✅ CORRECT: Bounded thread pool with bounded queue
        int corePoolSize = 10;
        int maxPoolSize = 50;
        int queueCapacity = 1000;
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,                          // Core threads
            maxPoolSize,                           // Max threads
            60L, TimeUnit.SECONDS,                 // Keep-alive for idle threads
            new LinkedBlockingQueue<>(queueCapacity), // Bounded queue!
            new ThreadFactory() {                   // Named threads for debugging
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "notification-worker-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // When queue is full, caller executes
        );
        
        System.out.println("Thread pool config:");
        System.out.println("  Core size: " + corePoolSize);
        System.out.println("  Max size: " + maxPoolSize);
        System.out.println("  Queue capacity: " + queueCapacity);
        System.out.println("  Rejection policy: CallerRunsPolicy (backpressure)\n");
        
        System.out.println("Simulating 50,000 orders (same load that crashed Bug 1)...\n");
        
        long startTime = System.currentTimeMillis();
        AtomicInteger rejections = new AtomicInteger(0);
        
        for (int orderId = 1; orderId <= 50_000; orderId++) {
            final int id = orderId;
            
            try {
                executor.submit(() -> {
                    sendOrderNotification(id);
                    notificationsSent.incrementAndGet();
                });
            } catch (RejectedExecutionException e) {
                rejections.incrementAndGet();
            }
            
            if (orderId % 10_000 == 0) {
                System.out.println("Progress: " + orderId + "/50000" +
                    ", Pool size: " + executor.getPoolSize() +
                    ", Queue: " + executor.getQueue().size() +
                    ", Completed: " + executor.getCompletedTaskCount());
            }
        }
        
        // ✅ ALWAYS shutdown executor!
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        System.out.println("\n✅ SUCCESS! Processed 50,000 orders without crashing!");
        System.out.println("   Time: " + elapsed + "ms");
        System.out.println("   Notifications sent: " + notificationsSent.get());
        System.out.println("   Max threads used: " + maxPoolSize + " (bounded!)");
        System.out.println("   Peak active threads: " + Thread.activeCount());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Simulated Services
    // ═══════════════════════════════════════════════════════════════════════════
    
    static void sendOrderNotification(int orderId) {
        // Simulate sending push notification
        try {
            Thread.sleep(10); // 10ms to send notification
            notificationsSent.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    static void sendSlowNotification(int orderId) {
        // Simulate slow external service
        try {
            Thread.sleep(5000); // 5 seconds!
            notificationsSent.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    static void updateInventory(int orderId) {
        // Simulate inventory update
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
