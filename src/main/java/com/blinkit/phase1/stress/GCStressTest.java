package com.blinkit.phase1.stress;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GC Stress Test for Blinkit Project
 * 
 * Demonstrates real-world GC scenarios in a quick-commerce application.
 * Run with GC logging enabled:
 * 
 *   java -Xms256m -Xmx256m -Xlog:gc*:stdout:time GCStressTest
 * 
 * Or for more detailed analysis:
 *   java -Xms256m -Xmx256m -Xlog:gc*:file=gc.log:time,level,tags GCStressTest
 * 
 * Scenarios covered:
 *   1. Bulk Product Upload (CSV-like processing)
 *   2. Flash Sale Order Burst
 *   3. Large Search Results
 *   4. Cart Operations Under Load
 *   5. Memory Leak Simulation
 */
public class GCStressTest {

    // Simulated entities (mimicking Blinkit domain)
    record ProductEntity(UUID id, String name, String brand, String category, 
                         BigDecimal price, boolean active, Instant createdAt) {}
    
    record CartItem(UUID productId, String name, int quantity, BigDecimal price) {}
    
    record OrderEntity(UUID id, String userId, List<OrderItem> items, 
                       BigDecimal total, String status, Instant createdAt) {}
    
    record OrderItem(UUID productId, String name, int quantity, 
                     BigDecimal unitPrice, BigDecimal lineTotal) {}
    
    record OutboxEvent(UUID id, UUID aggregateId, String eventType, 
                       String payload, Instant createdAt) {}

    // Metrics
    private static final AtomicInteger objectsCreated = new AtomicInteger(0);
    private static final AtomicLong bytesAllocated = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          BLINKIT GC STRESS TEST                              ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Run with: java -Xms256m -Xmx256m                            ║");
        System.out.println("║            -Xlog:gc*:stdout:time GCStressTest                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        printMemoryStats("INITIAL STATE");

        // Run each scenario
        scenario1_BulkUploadWithoutBatching();
        scenario2_BulkUploadWithBatching();
        scenario3_FlashSaleOrderBurst();
        scenario4_LargeSearchResults();
        scenario5_CartOperationsUnderLoad();
        scenario6_MemoryLeakSimulation();
        
        // ⚠️ This WILL cause OOM and generate heap dump!
        scenario7_ForceOOM_ForHeapDump();

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    ALL SCENARIOS COMPLETE                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        printMemoryStats("FINAL STATE");
        System.out.println("\nTotal objects created: " + objectsCreated.get());
        System.out.println("Estimated bytes allocated: " + formatBytes(bytesAllocated.get()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 1: Bulk Upload WITHOUT Batching (BAD - causes GC pressure)
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario1_BulkUploadWithoutBatching() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 1: Bulk Upload WITHOUT Batching (50K products)      │");
        System.out.println("│ Simulates: ProductBulkUploadService without em.clear()       │");
        System.out.println("│ Expected: Heavy GC activity, potential OOM with small heap   │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before bulk upload (no batching)");
        
        Instant start = Instant.now();
        
        // ❌ BAD PATTERN: Keep all objects in memory
        List<ProductEntity> allProducts = new ArrayList<>();
        List<OutboxEvent> allOutboxEvents = new ArrayList<>();
        
        int productCount = 50_000;
        
        for (int i = 0; i < productCount; i++) {
            // Simulate CSV row processing - creates multiple objects per row
            UUID productId = UUID.randomUUID();
            Instant now = Instant.now();
            
            ProductEntity product = new ProductEntity(
                productId,
                "Product-" + i + "-" + UUID.randomUUID().toString().substring(0, 8),
                "Brand-" + (i % 100),
                "Category-" + (i % 20),
                BigDecimal.valueOf(10 + (i % 1000)),
                true,
                now
            );
            
            // Simulate JSON payload creation (like objectMapper.writeValueAsString)
            String payload = """
                {"id":"%s","name":"%s","brand":"%s","category":"%s","price":%s}
                """.formatted(product.id(), product.name(), product.brand(), 
                             product.category(), product.price());
            
            OutboxEvent outbox = new OutboxEvent(
                UUID.randomUUID(),
                productId,
                "PRODUCT_UPSERTED",
                payload,
                now
            );
            
            allProducts.add(product);
            allOutboxEvents.add(outbox);
            objectsCreated.addAndGet(3); // product + outbox + payload string
            bytesAllocated.addAndGet(500); // ~500 bytes per product
            
            if (i > 0 && i % 10000 == 0) {
                System.out.println("  Processed " + i + " products...");
                printMemoryStats("  At " + i + " products");
            }
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        System.out.println("\n  ✓ Created " + productCount + " products in " + elapsed.toMillis() + "ms");
        System.out.println("  ✓ List sizes: products=" + allProducts.size() + 
                          ", outbox=" + allOutboxEvents.size());
        
        printMemoryStats("After bulk upload (before clearing)");
        
        // Simulate saving to DB and clearing
        allProducts.clear();
        allOutboxEvents.clear();
        System.gc(); // Request GC to reclaim (for demo purposes)
        
        sleep(500);
        printMemoryStats("After clearing lists");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 2: Bulk Upload WITH Batching (GOOD - controlled GC)
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario2_BulkUploadWithBatching() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 2: Bulk Upload WITH Batching (50K products)         │");
        System.out.println("│ Simulates: ProductBulkUploadService with em.clear()          │");
        System.out.println("│ Expected: Smooth sawtooth GC pattern, no OOM                 │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before bulk upload (with batching)");
        
        Instant start = Instant.now();
        
        int productCount = 50_000;
        int batchSize = 200; // Same as Blinkit's ProductBulkUploadService
        int batchesProcessed = 0;
        
        // ✅ GOOD PATTERN: Process in batches, clear after each
        List<ProductEntity> productBatch = new ArrayList<>(batchSize);
        List<OutboxEvent> outboxBatch = new ArrayList<>(batchSize);
        
        for (int i = 0; i < productCount; i++) {
            UUID productId = UUID.randomUUID();
            Instant now = Instant.now();
            
            ProductEntity product = new ProductEntity(
                productId,
                "Product-" + i,
                "Brand-" + (i % 100),
                "Category-" + (i % 20),
                BigDecimal.valueOf(10 + (i % 1000)),
                true,
                now
            );
            
            String payload = """
                {"id":"%s","name":"%s"}
                """.formatted(product.id(), product.name());
            
            OutboxEvent outbox = new OutboxEvent(
                UUID.randomUUID(), productId, "PRODUCT_UPSERTED", payload, now
            );
            
            productBatch.add(product);
            outboxBatch.add(outbox);
            objectsCreated.addAndGet(3);
            bytesAllocated.addAndGet(500);
            
            // Flush batch when full (mimics em.flush() + em.clear())
            if (productBatch.size() >= batchSize) {
                // Simulate DB save
                simulateDbSave(productBatch.size());
                
                // Clear batch - allows GC to reclaim
                productBatch.clear();
                outboxBatch.clear();
                batchesProcessed++;
                
                if (batchesProcessed % 50 == 0) {
                    System.out.println("  Flushed batch #" + batchesProcessed + 
                                      " (" + (batchesProcessed * batchSize) + " products)");
                }
            }
        }
        
        // Final batch
        if (!productBatch.isEmpty()) {
            simulateDbSave(productBatch.size());
            productBatch.clear();
            outboxBatch.clear();
            batchesProcessed++;
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        System.out.println("\n  ✓ Processed " + productCount + " products in " + 
                          batchesProcessed + " batches");
        System.out.println("  ✓ Total time: " + elapsed.toMillis() + "ms");
        
        printMemoryStats("After batched upload");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 3: Flash Sale Order Burst
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario3_FlashSaleOrderBurst() throws Exception {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 3: Flash Sale - 1000 Concurrent Orders              │");
        System.out.println("│ Simulates: OrderService.placeOrder() under extreme load      │");
        System.out.println("│ Expected: High Young Gen allocation, frequent Minor GC       │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before flash sale");
        
        // Pre-create some "products" in our simulated DB
        List<ProductEntity> catalog = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            catalog.add(new ProductEntity(
                UUID.randomUUID(),
                "FlashProduct-" + i,
                "Brand",
                "Category",
                BigDecimal.valueOf(99 + i),
                true,
                Instant.now()
            ));
        }
        
        int orderCount = 1000;
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(orderCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        Instant start = Instant.now();
        
        System.out.println("  Starting " + orderCount + " concurrent orders with " + 
                          threadCount + " threads...\n");
        
        for (int i = 0; i < orderCount; i++) {
            final int orderNum = i;
            executor.submit(() -> {
                try {
                    // Simulate placeOrder()
                    OrderEntity order = simulatePlaceOrder(catalog, orderNum);
                    if (order != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all orders
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        System.out.println("\n  ✓ Flash sale completed in " + elapsed.toMillis() + "ms");
        System.out.println("  ✓ Successful orders: " + successCount.get());
        System.out.println("  ✓ Failed orders: " + failCount.get());
        System.out.println("  ✓ Throughput: " + (orderCount * 1000 / elapsed.toMillis()) + " orders/sec");
        
        printMemoryStats("After flash sale");
    }

    static OrderEntity simulatePlaceOrder(List<ProductEntity> catalog, int orderNum) {
        Random rand = new Random();
        String cartId = UUID.randomUUID().toString();
        
        // Simulate cart with 3-10 items
        int itemCount = 3 + rand.nextInt(8);
        List<CartItem> cartItems = new ArrayList<>(itemCount);
        
        for (int i = 0; i < itemCount; i++) {
            ProductEntity product = catalog.get(rand.nextInt(catalog.size()));
            cartItems.add(new CartItem(
                product.id(),
                product.name(),
                1 + rand.nextInt(3),
                product.price()
            ));
            objectsCreated.incrementAndGet();
            bytesAllocated.addAndGet(100);
        }
        
        // Create order items (like OrderService does)
        List<OrderItem> orderItems = new ArrayList<>(itemCount);
        BigDecimal total = BigDecimal.ZERO;
        
        for (CartItem cartItem : cartItems) {
            BigDecimal lineTotal = cartItem.price().multiply(BigDecimal.valueOf(cartItem.quantity()));
            
            OrderItem orderItem = new OrderItem(
                cartItem.productId(),
                cartItem.name(),
                cartItem.quantity(),
                cartItem.price(),
                lineTotal
            );
            
            orderItems.add(orderItem);
            total = total.add(lineTotal);
            objectsCreated.incrementAndGet();
            bytesAllocated.addAndGet(150);
        }
        
        // Create order
        OrderEntity order = new OrderEntity(
            UUID.randomUUID(),
            cartId,
            orderItems,
            total,
            "PLACED",
            Instant.now()
        );
        
        objectsCreated.incrementAndGet();
        bytesAllocated.addAndGet(200);
        
        // Simulate DB save latency
        sleep(5);
        
        return order;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 4: Large Search Results
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario4_LargeSearchResults() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 4: Large Search Results (10K results × 10 queries)  │");
        System.out.println("│ Simulates: ProductSearchService with no size limit           │");
        System.out.println("│ Expected: Large allocations, potential Full GC               │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before search stress");
        
        int queryCount = 10;
        int resultsPerQuery = 10_000; // ❌ No size limit!
        
        for (int q = 0; q < queryCount; q++) {
            System.out.println("  Executing search query #" + (q + 1) + "...");
            
            // Simulate Elasticsearch response parsing
            List<ProductEntity> searchResults = new ArrayList<>(resultsPerQuery);
            
            // Simulate large JSON response string (like ES returns)
            StringBuilder jsonBuilder = new StringBuilder(resultsPerQuery * 200);
            jsonBuilder.append("{\"hits\":{\"hits\":[");
            
            for (int i = 0; i < resultsPerQuery; i++) {
                ProductEntity product = new ProductEntity(
                    UUID.randomUUID(),
                    "SearchResult-" + i,
                    "Brand-" + (i % 50),
                    "Category-" + (i % 10),
                    BigDecimal.valueOf(100 + i),
                    true,
                    Instant.now()
                );
                searchResults.add(product);
                
                // Append to JSON (simulating ES response)
                if (i > 0) jsonBuilder.append(",");
                jsonBuilder.append("{\"_source\":{\"name\":\"").append(product.name()).append("\"}}");
                
                objectsCreated.incrementAndGet();
                bytesAllocated.addAndGet(300);
            }
            jsonBuilder.append("]}}");
            
            // The JSON string itself consumes significant memory
            String jsonResponse = jsonBuilder.toString();
            bytesAllocated.addAndGet(jsonResponse.length() * 2); // chars are 2 bytes
            
            System.out.println("    → Returned " + searchResults.size() + " results, " +
                              "JSON size: " + formatBytes(jsonResponse.length() * 2));
            
            // Results go out of scope here, eligible for GC
        }
        
        printMemoryStats("After search stress");
        
        System.gc();
        sleep(500);
        printMemoryStats("After GC");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 5: Cart Operations Under Load
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario5_CartOperationsUnderLoad() throws Exception {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 5: Cart Operations (5000 add/remove cycles)         │");
        System.out.println("│ Simulates: CartService toResponse() creating DTOs            │");
        System.out.println("│ Expected: Healthy Minor GC pattern (short-lived objects)     │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before cart operations");
        
        int operationCount = 5000;
        Map<String, List<CartItem>> carts = new ConcurrentHashMap<>();
        
        Random rand = new Random();
        Instant start = Instant.now();
        
        for (int i = 0; i < operationCount; i++) {
            String cartId = "cart-" + (i % 100); // 100 different carts
            
            // Get or create cart
            List<CartItem> cart = carts.computeIfAbsent(cartId, k -> new ArrayList<>());
            
            // Random operation: add, update, or remove
            int op = rand.nextInt(3);
            
            if (op == 0 || cart.isEmpty()) {
                // Add item
                CartItem item = new CartItem(
                    UUID.randomUUID(),
                    "CartProduct-" + rand.nextInt(1000),
                    1 + rand.nextInt(5),
                    BigDecimal.valueOf(50 + rand.nextInt(200))
                );
                cart.add(item);
                objectsCreated.incrementAndGet();
                bytesAllocated.addAndGet(100);
            } else if (op == 1 && !cart.isEmpty()) {
                // Update quantity (creates new CartItem since records are immutable)
                int idx = rand.nextInt(cart.size());
                CartItem old = cart.get(idx);
                cart.set(idx, new CartItem(old.productId(), old.name(), 
                                          old.quantity() + 1, old.price()));
                objectsCreated.incrementAndGet();
            } else if (!cart.isEmpty()) {
                // Remove item
                cart.remove(rand.nextInt(cart.size()));
            }
            
            // Simulate toResponse() - creates DTOs (short-lived)
            simulateCartToResponse(cart);
            
            if (i > 0 && i % 1000 == 0) {
                System.out.println("  Processed " + i + " cart operations...");
            }
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        System.out.println("\n  ✓ Completed " + operationCount + " cart operations in " + 
                          elapsed.toMillis() + "ms");
        System.out.println("  ✓ Active carts: " + carts.size());
        
        printMemoryStats("After cart operations");
    }

    static void simulateCartToResponse(List<CartItem> cart) {
        // Mimics CartService.toResponse() - creates response DTOs
        List<Map<String, Object>> itemResponses = new ArrayList<>(cart.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        
        for (CartItem item : cart) {
            BigDecimal lineTotal = item.price().multiply(BigDecimal.valueOf(item.quantity()));
            subtotal = subtotal.add(lineTotal);
            
            // Create response DTO (short-lived, for JSON serialization)
            Map<String, Object> dto = new HashMap<>();
            dto.put("productId", item.productId());
            dto.put("name", item.name());
            dto.put("quantity", item.quantity());
            dto.put("price", item.price());
            dto.put("lineTotal", lineTotal);
            
            itemResponses.add(dto);
            objectsCreated.addAndGet(2); // Map + lineTotal BigDecimal
            bytesAllocated.addAndGet(150);
        }
        
        // These go out of scope immediately → perfect for Young Gen GC!
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 6: Memory Leak Simulation
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario6_MemoryLeakSimulation() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 6: Memory Leak Simulation                           │");
        System.out.println("│ Simulates: Forgetting to clear a cache, event listeners      │");
        System.out.println("│ Expected: Heap grows, GC can't reclaim, eventually OOM       │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before memory leak");
        
        // ❌ BAD: Static collection that grows forever (like a forgotten cache)
        List<byte[]> leakyCache = new ArrayList<>();
        
        System.out.println("  Simulating memory leak (adding to cache without eviction)...");
        System.out.println("  Watch the 'Used' memory grow while 'Free' shrinks!\n");
        
        int iterations = 0;
        int maxIterations = 100; // Limit to avoid actual OOM
        
        try {
            while (iterations < maxIterations) {
                // Simulate caching large objects without eviction
                byte[] largeObject = new byte[1024 * 1024]; // 1 MB
                leakyCache.add(largeObject);
                iterations++;
                bytesAllocated.addAndGet(1024 * 1024);
                
                if (iterations % 10 == 0) {
                    System.out.println("  Leaked " + iterations + " MB...");
                    printMemoryStats("  At " + iterations + " MB leaked");
                }
                
                // Check if we're getting close to OOM
                Runtime rt = Runtime.getRuntime();
                long freeMemory = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
                if (freeMemory < 50 * 1024 * 1024) { // Less than 50MB free
                    System.out.println("\n  ⚠️  Low memory detected! Stopping leak simulation.");
                    System.out.println("  ⚠️  In production, this would cause OutOfMemoryError!");
                    break;
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  🔴 OutOfMemoryError caught! This is what happens with memory leaks.");
        }
        
        // Clean up
        System.out.println("\n  Clearing leaked cache...");
        leakyCache.clear();
        System.gc();
        sleep(1000);
        
        printMemoryStats("After clearing leak and GC");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 7: Force OutOfMemoryError (For Heap Dump Generation)
    // ═══════════════════════════════════════════════════════════════════════════
    static void scenario7_ForceOOM_ForHeapDump() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│ SCENARIO 7: Force OutOfMemoryError (Heap Dump Generation)    │");
        System.out.println("│                                                              │");
        System.out.println("│ ⚠️  WARNING: This WILL crash the JVM with OOM!               │");
        System.out.println("│ ⚠️  Run with: -XX:+HeapDumpOnOutOfMemoryError                │");
        System.out.println("│ ⚠️           -XX:HeapDumpPath=C:\\heapdumps\\                  │");
        System.out.println("│                                                              │");
        System.out.println("│ Simulates: Unbounded cache growth in production              │");
        System.out.println("│ Purpose: Generate .hprof file for Eclipse MAT analysis       │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");

        printMemoryStats("Before forcing OOM");
        
        // Simulate realistic leak: ProductEntity cache that never evicts
        List<ProductEntity> productCache = new ArrayList<>();
        // Simulate order history that keeps growing  
        List<OrderEntity> orderHistory = new ArrayList<>();
        // Simulate session data that's never cleaned
        Map<String, List<CartItem>> abandonedCarts = new HashMap<>();
        
        System.out.println("  Simulating production memory leak scenarios...");
        System.out.println("  - Unbounded product cache");
        System.out.println("  - Order history without pagination");
        System.out.println("  - Abandoned cart sessions never expiring\n");
        
        int iteration = 0;
        
        // This WILL cause OutOfMemoryError!
        while (true) {
            iteration++;
            
            // Leak 1: Product cache grows unbounded (like missing eviction policy)
            for (int i = 0; i < 100; i++) {
                productCache.add(new ProductEntity(
                    UUID.randomUUID(),
                    "CachedProduct-" + iteration + "-" + i,
                    "Brand-" + (i % 50),
                    "Category-" + (i % 10),
                    BigDecimal.valueOf(99.99),
                    true,
                    Instant.now()
                ));
            }
            
            // Leak 2: Order history without cleanup
            List<OrderItem> items = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                items.add(new OrderItem(
                    UUID.randomUUID(),
                    "Product-" + i,
                    2,
                    BigDecimal.valueOf(50),
                    BigDecimal.valueOf(100)
                ));
            }
            orderHistory.add(new OrderEntity(
                UUID.randomUUID(),
                "user-" + iteration,
                items,
                BigDecimal.valueOf(500),
                "COMPLETED",
                Instant.now()
            ));
            
            // Leak 3: Abandoned carts (session never expires)
            String cartId = "cart-" + iteration;
            List<CartItem> cartItems = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                cartItems.add(new CartItem(
                    UUID.randomUUID(),
                    "CartProduct-" + i,
                    1 + (i % 5),
                    BigDecimal.valueOf(25 + i)
                ));
            }
            abandonedCarts.put(cartId, cartItems);
            
            if (iteration % 100 == 0) {
                System.out.println("  Iteration " + iteration + 
                    ": products=" + productCache.size() + 
                    ", orders=" + orderHistory.size() + 
                    ", carts=" + abandonedCarts.size());
                printMemoryStats("  At iteration " + iteration);
            }
        }
        // OutOfMemoryError will be thrown, heap dump created automatically!
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    static void printMemoryStats(String label) {
        Runtime rt = Runtime.getRuntime();
        long totalMemory = rt.totalMemory();
        long freeMemory = rt.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = rt.maxMemory();
        
        System.out.println("  ┌─ " + label + " ─");
        System.out.println("  │ Used:  " + formatBytes(usedMemory) + 
                          " / " + formatBytes(maxMemory) + 
                          " (" + (usedMemory * 100 / maxMemory) + "%)");
        System.out.println("  │ Free:  " + formatBytes(freeMemory));
        System.out.println("  └─────────────────────────────────");
    }
    
    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    static void simulateDbSave(int count) {
        // Simulate I/O latency
        sleep(1);
    }
    
    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
