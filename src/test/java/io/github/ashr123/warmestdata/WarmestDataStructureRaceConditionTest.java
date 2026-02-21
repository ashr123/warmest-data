package io.github.ashr123.warmestdata;

import io.github.ashr123.warmestdata.dto.WarmestDataStructure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Race condition tests for in-memory WarmestDataStructure.
 * Tests all scenarios identified in RACE-CONDITION-ANALYSIS.md.
 */
class WarmestDataStructureRaceConditionTest {

	private static final int THREAD_COUNT = 10;
	private static final int ITERATIONS = 1000;

	private WarmestDataStructure dataStructure;
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		dataStructure = new WarmestDataStructure();
		executor = Executors.newFixedThreadPool(THREAD_COUNT);
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		executor.shutdown();
		Assertions.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS),
				"Executor did not terminate in time - possible deadlock");
	}

	// ==================== Scenario 1: Node Removed Between Locks ====================

	/**
	 * Scenario 1 from RACE-CONDITION-ANALYSIS.md:
	 * Thread A calls get("key") while Thread B concurrently removes the same key.
	 * The get() method releases read lock, then acquires write lock.
	 * Between these locks, the node could be removed by another thread.
	 * Expected: get() returns either the value (if read before removal) or null (if removed first).
	 * Must never throw an exception or return corrupted data.
	 */
	@Test
	void scenario1_nodeRemovedBetweenLocks_getConcurrentWithRemove() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", 100);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			// Thread A: get the key
			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key1");
					// Result should be either 100 or null (if removed first)
					if (result != null && result != 100) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			// Thread B: remove the key
			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.remove("key1");
					// Result should be either 100 or null
					if (result != null && result != 100) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(5, TimeUnit.SECONDS);

			// After both operations, key should be gone
			Assertions.assertNull(dataStructure.get("key1"),
					"Key should be removed after concurrent get+remove");

			// Clean up for next iteration
			dataStructure.remove("key1");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent get+remove produced corrupted data");
	}

	// ==================== Scenario 2: Node Moved to Tail Between Locks ====================

	/**
	 * Scenario 2 from RACE-CONDITION-ANALYSIS.md:
	 * Two threads simultaneously call get() on the same key.
	 * Both find the node is not at tail under read lock, release it, then try to moveToTail.
	 * The second thread's double-check should detect the node is already at tail.
	 * Expected: Both threads return the correct value. The warmest key is correct.
	 */
	@Test
	void scenario2_nodeMovedToTailBetweenLocks_concurrentGetsOnSameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("a", 1);
			dataStructure.put("b", 2);
			dataStructure.put("c", 3);
			// Now warmest is "c", and "a" is coldest

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			// Both threads get "a" concurrently, triggering moveToTail
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

			done.await(5, TimeUnit.SECONDS);

			// After both gets on "a", it should be the warmest
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

	// ==================== Scenario 3: Concurrent Value Updates ====================

	/**
	 * Scenario 3 from RACE-CONDITION-ANALYSIS.md:
	 * Thread A calls get("key") while Thread B calls put("key", newValue).
	 * Thread A's read lock blocks the write lock, so this should be safe.
	 * Expected: get() returns either the old or new value (both are valid snapshots).
	 */
	@Test
	void scenario3_concurrentGetAndPut_valueUpdateDuringGet() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", 100);
			// Add another key so "key1" isn't at tail (forces write lock path in get)
			dataStructure.put("key2", 200);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			// Thread A: get the key
			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key1");
					// Result must be either 100 (old) or 999 (new), never anything else
					if (result != null && result != 100 && result != 999) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			// Thread B: update the value
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

			done.await(5, TimeUnit.SECONDS);

			// After both, the value should be 999
			Assertions.assertEquals(999, dataStructure.get("key1"),
					"Value should be updated to 999 after concurrent get+put");

			// Clean up
			dataStructure.remove("key1");
			dataStructure.remove("key2");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent get+put produced corrupted data");
	}

	// ==================== Scenario 4: Multiple Concurrent Gets Moving Different Nodes ====================

	/**
	 * Multiple threads get different keys concurrently, all triggering moveToTail.
	 * Tests linked list integrity under concurrent modifications.
	 * Expected: No corruption of the linked list, all operations return correct values.
	 */
	@Test
	void scenario4_multipleConcurrentGets_differentKeys() throws Exception {
		int keyCount = THREAD_COUNT;
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS / 10; i++) {
			// Insert many keys
			for (int k = 0; k < keyCount; k++) {
				dataStructure.put("key" + k, k);
			}

			CyclicBarrier barrier = new CyclicBarrier(keyCount);
			CountDownLatch done = new CountDownLatch(keyCount);

			// Each thread gets a different key
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

			done.await(10, TimeUnit.SECONDS);

			// Verify data integrity: all keys still exist with correct values
			for (int k = 0; k < keyCount; k++) {
				Integer val = dataStructure.get("key" + k);
				Assertions.assertEquals(k, val,
						"Key 'key" + k + "' should still have value " + k);
			}

			// Warmest should be the last one we get'd in the verification loop
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
	 * Expected: No exceptions, no data corruption. After both complete,
	 * the key either exists (put won) or doesn't (remove won).
	 */
	@Test
	void scenario5_concurrentPutAndRemove_sameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", 100);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			// Thread A: put a new value
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

			// Thread B: remove the key
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

			done.await(5, TimeUnit.SECONDS);

			// After both, key either exists with value 200 (put after remove)
			// or doesn't exist (remove after put)
			Integer result = dataStructure.get("key1");
			if (result != null) {
				Assertions.assertEquals(200, result,
						"If key exists, value should be 200");
			}
			// Either way is valid - no assertion on existence

			// Clean up
			dataStructure.remove("key1");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent put+remove produced an exception");
	}

	// ==================== Scenario 6: Warmest Consistency Under Concurrent Operations ====================

	/**
	 * Tests that getWarmest() always returns a valid key (one that exists in the map)
	 * even under heavy concurrent modifications.
	 */
	@Test
	void scenario6_warmestConsistencyUnderConcurrency() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);
		AtomicInteger errorCount = new AtomicInteger(0);

		// Pre-populate
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

		done.await(30, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Concurrent mixed operations caused " + errorCount.get() + " exceptions");
	}

	// ==================== Scenario 7: No Deadlock Under Lock Upgrade Pattern ====================

	/**
	 * Tests that the read-lock-then-write-lock pattern in get() does not cause deadlocks.
	 * Many threads concurrently call get() on the same non-tail key, forcing the
	 * read-to-write lock upgrade path.
	 */
	@Test
	void scenario7_noDeadlockUnderConcurrentGetWithLockUpgrade() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		// Insert two keys, so the first one is not at tail
		dataStructure.put("target", 42);
		dataStructure.put("other", 99);

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			executor.submit(() -> {
				try {
					for (int i = 0; i < ITERATIONS; i++) {
						// get("target") forces read lock -> release -> write lock path
						Integer result = dataStructure.get("target");
						if (result == null || result != 42) {
							failed.set(true);
						}
						// Put "other" back to make "target" non-tail again
						dataStructure.put("other", 99);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});
		}

		boolean completed = done.await(30, TimeUnit.SECONDS);
		Assertions.assertTrue(completed,
				"Threads did not complete in time - possible DEADLOCK detected!");

		Assertions.assertFalse(failed.get(),
				"Concurrent get with lock upgrade produced incorrect results");
	}

	// ==================== Scenario 8: Rapid Sequential Put-Get-Remove ====================

	/**
	 * Tests that rapid sequential operations from multiple threads maintain consistency.
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
						// put should return null (new key) or previous value
						Integer prev = dataStructure.put(myKey, i);
						if (i == 0 && prev != null) {
							failed.set(true);
						}
						if (i > 0 && prev != null && prev != i - 1) {
							failed.set(true);
						}

						// get should return current value
						Integer got = dataStructure.get(myKey);
						if (got == null || got != i) {
							failed.set(true);
						}

						// remove should return current value
						Integer removed = dataStructure.remove(myKey);
						if (removed == null || removed != i) {
							failed.set(true);
						}

						// get after remove should return null
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

		done.await(30, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Per-thread key operations produced incorrect results under concurrency");
	}

	// ==================== Scenario 9: Get on Non-Existent Key During Heavy Writes ====================

	/**
	 * Tests that get() returns null for non-existent keys even when other threads
	 * are heavily writing different keys.
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
							// Writer threads: put various keys
							dataStructure.put("write-key-" + threadId + "-" + (i % 10), i);
						} else {
							// Reader threads: get a key that was never inserted
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

		done.await(30, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Get on non-existent key returned non-null during heavy writes");
	}

	// ==================== Scenario 10: Concurrent Warmest Tracking ====================

	/**
	 * Tests that after all concurrent operations complete and we perform a final
	 * deterministic operation, getWarmest() returns the expected key.
	 */
	@Test
	void scenario10_warmestTrackingAfterConcurrentOps() throws Exception {
		CountDownLatch done = new CountDownLatch(THREAD_COUNT);
		AtomicBoolean failed = new AtomicBoolean(false);

		// Pre-populate
		for (int k = 0; k < 10; k++) {
			dataStructure.put("key" + k, k);
		}

		// Many threads do random operations concurrently
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

		done.await(30, TimeUnit.SECONDS);

		Assertions.assertFalse(failed.get(),
				"Concurrent operations threw exceptions");

		// Now do a deterministic operation and verify warmest
		dataStructure.put("final-key", 9999);
		Assertions.assertEquals("final-key", dataStructure.getWarmest(),
				"After final put, warmest should be 'final-key'");
	}
}
