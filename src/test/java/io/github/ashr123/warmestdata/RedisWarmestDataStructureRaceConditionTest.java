package io.github.ashr123.warmestdata;

import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Race condition tests for Redis-backed WarmestDataStructure using Testcontainers.
 * Tests the same concurrency scenarios as WarmestDataStructureRaceConditionTest
 * but against the Redis implementation (Lua scripts guarantee atomicity).
 */
@SpringBootTest
@ActiveProfiles("redis")
@Import(TestcontainersConfiguration.class)
class RedisWarmestDataStructureRaceConditionTest {

	private static final int THREAD_COUNT = 10;
	private static final int ITERATIONS = 200; // Lower for Redis due to network overhead

	@Autowired
	private WarmestDataStructureInterface dataStructure;

	@BeforeEach
	void setUp() {
		// Clear Redis data before each test
		while (dataStructure.getWarmest() != null) {
			dataStructure.remove(dataStructure.getWarmest());
		}
	}

	private ExecutorService executor;

	@BeforeEach
	void setUpExecutor() {
		executor = Executors.newFixedThreadPool(THREAD_COUNT);
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		executor.shutdown();
		Assertions.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS),
				"Executor did not terminate in time - possible deadlock");
	}

	// ==================== Scenario 1: Node Removed Between Locks ====================

	/**
	 * Scenario 1: Thread A calls get("key") while Thread B concurrently removes the same key.
	 * For Redis with Lua scripts, each operation is atomic, so the result depends on
	 * which script runs first.
	 * Expected: get() returns either the value or null. Never throws or returns corrupted data.
	 */
	@Test
	void scenario1_concurrentGetAndRemove_sameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", 100);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key1");
					if (result != null && result != 100) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.remove("key1");
					if (result != null && result != 100) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(10, TimeUnit.SECONDS);

			// After both operations, key should be gone
			Assertions.assertNull(dataStructure.get("key1"),
					"Key should be removed after concurrent get+remove");

			// Clean up
			dataStructure.remove("key1");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent get+remove produced corrupted data");
	}

	// ==================== Scenario 2: Concurrent Gets on Same Key ====================

	/**
	 * Scenario 2: Two threads simultaneously call get() on the same key.
	 * Both should see the correct value and the key should become warmest.
	 */
	@Test
	void scenario2_concurrentGetsOnSameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("a", 1);
			dataStructure.put("b", 2);
			dataStructure.put("c", 3);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			for (int t = 0; t < 2; t++) {
				executor.submit(() -> {
					try {
						barrier.await(5, TimeUnit.SECONDS);
						Integer result = dataStructure.get("a");
						if (result == null || result != 1) {
							failed.set(true);
						}
					} catch (Exception e) {
						failed.set(true);
					} finally {
						done.countDown();
					}
				});
			}

			done.await(10, TimeUnit.SECONDS);

			Assertions.assertEquals("a", dataStructure.getWarmest(),
					"After concurrent gets on 'a', 'a' should be warmest");

			// Clean up
			dataStructure.remove("a");
			dataStructure.remove("b");
			dataStructure.remove("c");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent gets on same key produced incorrect results");
	}

	// ==================== Scenario 3: Concurrent Get and Put ====================

	/**
	 * Scenario 3: Thread A calls get("key") while Thread B calls put("key", newValue).
	 * Expected: get() returns either old or new value. Never corrupted data.
	 */
	@Test
	void scenario3_concurrentGetAndPut_sameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", 100);
			dataStructure.put("key2", 200);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key1");
					if (result != null && result != 100 && result != 999) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					dataStructure.put("key1", 999);
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(10, TimeUnit.SECONDS);

			Assertions.assertEquals(999, dataStructure.get("key1"),
					"Value should be updated to 999 after concurrent get+put");

			// Clean up
			dataStructure.remove("key1");
			dataStructure.remove("key2");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent get+put produced corrupted data");
	}

	// ==================== Scenario 4: Multiple Concurrent Gets on Different Keys ====================

	/**
	 * Multiple threads get different keys concurrently.
	 * Tests data structure integrity under concurrent read+move operations.
	 */
	@Test
	void scenario4_multipleConcurrentGets_differentKeys() throws Exception {
		int keyCount = 10;
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS / 10; i++) {
			for (int k = 0; k < keyCount; k++) {
				dataStructure.put("key" + k, k);
			}

			CyclicBarrier barrier = new CyclicBarrier(keyCount);
			CountDownLatch done = new CountDownLatch(keyCount);

			for (int k = 0; k < keyCount; k++) {
				final int key = k;
				executor.submit(() -> {
					try {
						barrier.await(5, TimeUnit.SECONDS);
						Integer result = dataStructure.get("key" + key);
						if (result == null || result != key) {
							failed.set(true);
						}
					} catch (Exception e) {
						failed.set(true);
					} finally {
						done.countDown();
					}
				});
			}

			done.await(15, TimeUnit.SECONDS);

			for (int k = 0; k < keyCount; k++) {
				Integer val = dataStructure.get("key" + k);
				Assertions.assertEquals(k, val,
						"Key 'key" + k + "' should still have value " + k);
			}

			Assertions.assertNotNull(dataStructure.getWarmest(),
					"Warmest should not be null after operations");

			// Clean up
			for (int k = 0; k < keyCount; k++) {
				dataStructure.remove("key" + k);
			}
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent gets on different keys produced incorrect results");
	}

	// ==================== Scenario 5: Concurrent Put and Remove on Same Key ====================

	/**
	 * Tests put and remove happening concurrently on the same key.
	 * Expected: No exceptions, no data corruption.
	 */
	@Test
	void scenario5_concurrentPutAndRemove_sameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", 100);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					dataStructure.put("key1", 200);
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					dataStructure.remove("key1");
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(10, TimeUnit.SECONDS);

			Integer result = dataStructure.get("key1");
			if (result != null) {
				Assertions.assertEquals(200, result,
						"If key exists, value should be 200");
			}

			// Clean up
			dataStructure.remove("key1");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent put+remove produced an exception");
	}

	// ==================== Scenario 6: Warmest Consistency Under Concurrent Operations ====================

	/**
	 * Tests that mixed concurrent operations (put, get, remove, getWarmest) don't throw exceptions
	 * or corrupt the data structure.
	 */
	@Test
	void scenario6_warmestConsistencyUnderConcurrency() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);
		AtomicInteger errorCount = new AtomicInteger(0);

		for (int k = 0; k < 5; k++) {
			dataStructure.put("key" + k, k * 100);
		}

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			executor.submit(() -> {
				try {
					ThreadLocalRandom rng = ThreadLocalRandom.current();
					for (int i = 0; i < ITERATIONS; i++) {
						int op = rng.nextInt(4);
						String key = "key" + rng.nextInt(10);
						switch (op) {
							case 0 -> dataStructure.put(key, rng.nextInt(1000));
							case 1 -> dataStructure.get(key);
							case 2 -> dataStructure.remove(key);
							case 3 -> dataStructure.getWarmest();
						}
					}
				} catch (Exception e) {
					failed.set(true);
					errorCount.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
		}

		done.await(60, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Concurrent mixed operations caused " + errorCount.get() + " exceptions");
	}

	// ==================== Scenario 7: No Deadlock Under Concurrent Gets ====================

	/**
	 * Tests that many threads concurrently calling get() on the same non-tail key
	 * complete in reasonable time (no deadlock/livelock).
	 * For Redis, Lua scripts are single-threaded so deadlocks are impossible,
	 * but we verify no timeouts or errors.
	 */
	@Test
	void scenario7_noConcurrencyIssuesUnderHeavyGets() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		dataStructure.put("target", 42);
		dataStructure.put("other", 99);

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			executor.submit(() -> {
				try {
					for (int i = 0; i < ITERATIONS; i++) {
						Integer result = dataStructure.get("target");
						if (result == null || result != 42) {
							failed.set(true);
						}
						dataStructure.put("other", 99);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});
		}

		boolean completed = done.await(60, TimeUnit.SECONDS);
		Assertions.assertTrue(completed,
				"Threads did not complete in time - possible concurrency issue!");

		Assertions.assertFalse(failed.get(),
				"Concurrent gets produced incorrect results");
	}

	// ==================== Scenario 8: Per-Thread Key Consistency ====================

	/**
	 * Each thread owns its own key and performs put-get-remove cycles.
	 * Expected: All operations return expected values for the thread's own key.
	 */
	@Test
	void scenario8_perThreadKeyConsistency() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					String myKey = "thread-" + threadId;
					for (int i = 0; i < ITERATIONS; i++) {
						Integer prev = dataStructure.put(myKey, i);
						if (i == 0 && prev != null) {
							failed.set(true);
						}
						if (i > 0 && prev != null && prev != i - 1) {
							failed.set(true);
						}

						Integer got = dataStructure.get(myKey);
						if (got == null || got != i) {
							failed.set(true);
						}

						Integer removed = dataStructure.remove(myKey);
						if (removed == null || removed != i) {
							failed.set(true);
						}

						Integer afterRemove = dataStructure.get(myKey);
						if (afterRemove != null) {
							failed.set(true);
						}
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});
		}

		done.await(60, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Per-thread key operations produced incorrect results under concurrency");
	}

	// ==================== Scenario 9: Get Non-Existent Key During Heavy Writes ====================

	/**
	 * Tests that get() returns null for non-existent keys during heavy concurrent writes.
	 */
	@Test
	void scenario9_getNonExistentKeyDuringHeavyWrites() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					for (int i = 0; i < ITERATIONS; i++) {
						if (threadId % 2 == 0) {
							dataStructure.put("write-key-" + threadId + "-" + (i % 10), i);
						} else {
							Integer result = dataStructure.get("never-inserted-" + threadId);
							if (result != null) {
								failed.set(true);
							}
						}
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});
		}

		done.await(60, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Get on non-existent key returned non-null during heavy writes");
	}

	// ==================== Scenario 10: Warmest Tracking After Concurrent Ops ====================

	/**
	 * Tests that after concurrent chaos, a final deterministic operation
	 * correctly sets the warmest key.
	 */
	@Test
	void scenario10_warmestTrackingAfterConcurrentOps() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int k = 0; k < 10; k++) {
			dataStructure.put("key" + k, k);
		}

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			executor.submit(() -> {
				try {
					ThreadLocalRandom rng = ThreadLocalRandom.current();
					for (int i = 0; i < ITERATIONS; i++) {
						String key = "key" + rng.nextInt(10);
						switch (rng.nextInt(3)) {
							case 0 -> dataStructure.put(key, rng.nextInt(1000));
							case 1 -> dataStructure.get(key);
							case 2 -> dataStructure.remove(key);
						}
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});
		}

		done.await(60, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Concurrent operations threw exceptions");

		dataStructure.put("final-key", 9999);
		Assertions.assertEquals("final-key", dataStructure.getWarmest(),
				"After final put, warmest should be 'final-key'");
	}
}
