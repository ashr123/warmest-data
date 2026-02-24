package io.github.ashr123.warmestdata;

import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class containing all race condition test scenarios.
 * Concrete subclasses activate the desired Spring profile, causing Spring to
 * inject either {@link io.github.ashr123.warmestdata.dto.WarmestDataStructure}
 * (profile {@code !redis}) or
 * {@link io.github.ashr123.warmestdata.dto.RedisWarmestDataStructure}
 * (profile {@code redis}).
 * <p>
 * Both implementations must satisfy exactly the same concurrency contracts.
 */
abstract class AbstractRaceConditionTest {

	private static final int THREAD_COUNT = 10;
	private static final int ITERATIONS = 1_000;

	@Autowired
	protected WarmestDataStructureInterface dataStructure;

	private ExecutorService executor;

	@BeforeEach
	void setUpExecutor() {
		executor = Executors.newFixedThreadPool(THREAD_COUNT);
	}

	@BeforeEach
	void clearDataStructure() {
		// Drain any leftover state from a previous test.
		// Works for both in-memory (idempotent) and Redis implementations.
		while (dataStructure.getWarmest() != null) {
			dataStructure.remove(dataStructure.getWarmest());
		}
	}

	@AfterEach
	void tearDownExecutor() throws InterruptedException {
		executor.shutdown();
		Assertions.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS),
				"Executor did not terminate in time — possible deadlock");
	}

	// ==================== Scenario 1: AT_TAIL path — get() racing with put() on the SAME tail key ====================

	/**
	 * This is the scenario the old in-memory code got WRONG.
	 * <p>
	 * Old code path:
	 *   readLock() → node = map.get(key) → node == tail → readUnlock() → return node.value  ← NO LOCK
	 * <p>
	 * The key is always the tail (only one key in the structure), so every get()
	 * takes the AT_TAIL fast path. A concurrent put() on the same key mutates
	 * node.value under write lock. Without any lock on the read side, the value
	 * read by get() had no JMM visibility guarantee — it could be stale or torn.
	 * <p>
	 * Fix: node.value is now read while the read lock is still held.
	 * <p>
	 * For Redis: each Lua script is atomic, so no partial state is ever visible.
	 * <p>
	 * Expected: every get() returns either OLD_VALUE or NEW_VALUE — never anything else.
	 */
	@Test
	void scenario1_atTailFastPath_getConcurrentWithPutOnSameKey() throws Exception {
		final int OLD_VALUE = 100;
		final int NEW_VALUE = 999;
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			// Only ONE key — it is always the tail, so get() always takes the AT_TAIL path
			dataStructure.put("key", OLD_VALUE);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			// Thread A: get — must hit the AT_TAIL fast path
			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key");
					// Valid outcomes: OLD_VALUE (read before put) or NEW_VALUE (read after put)
					if (result == null || (result != OLD_VALUE && result != NEW_VALUE)) {
						failed.set(true);
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			// Thread B: put — mutates node.value under write lock
			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					dataStructure.put("key", NEW_VALUE);
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(5, TimeUnit.SECONDS);
			dataStructure.remove("key");
		}

		Assertions.assertFalse(failed.get(),
				"AT_TAIL get() concurrent with put() returned a value that is neither old nor new");
	}

	// ==================== Scenario 2: AT_TAIL path — get() racing with remove() on the same tail key ====================

	/**
	 * The key is the only (tail) entry. Thread A calls get() — AT_TAIL fast path.
	 * Thread B calls remove() concurrently.
	 * <p>
	 * Old code: get() read node.value AFTER releasing the read lock, so it could
	 * observe the node after detach() had cleared its pointers.
	 * <p>
	 * Expected: get() returns VALUE or null — never throws, never returns garbage.
	 */
	@Test
	void scenario2_atTailFastPath_getConcurrentWithRemove() throws Exception {
		final int VALUE = 100;
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key", VALUE);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key");
					if (result != null && result != VALUE) {
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
					dataStructure.remove("key");
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(5, TimeUnit.SECONDS);
			dataStructure.remove("key"); // ensure clean state
		}

		Assertions.assertFalse(failed.get(),
				"AT_TAIL get() concurrent with remove() returned a corrupted value");
	}

	// ==================== Scenario 3: NEEDS_MOVE path — get() racing with remove() ====================

	/**
	 * "key" is NOT at tail ("other" is inserted after it). Thread A calls get("key")
	 * via the NEEDS_MOVE path (read lock → release → write lock).
	 * Thread B calls remove("key") concurrently.
	 * <p>
	 * The double-check inside the write-lock section must detect the removal.
	 * <p>
	 * Expected: get() returns VALUE or null — never throws.
	 */
	@Test
	void scenario3_needsMovePath_getConcurrentWithRemove() throws Exception {
		final int VALUE = 100;
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key", VALUE);
			dataStructure.put("other", 200); // makes "key" non-tail → NEEDS_MOVE path

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key");
					if (result != null && result != VALUE) {
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
					dataStructure.remove("key");
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(5, TimeUnit.SECONDS);

			// After both, "key" must be gone
			Assertions.assertNull(dataStructure.get("key"),
					"Key should be removed after concurrent get+remove on NEEDS_MOVE path");

			dataStructure.remove("other");
		}

		Assertions.assertFalse(failed.get(),
				"NEEDS_MOVE get() concurrent with remove() returned a corrupted value");
	}

	// ==================== Scenario 4: NEEDS_MOVE path — two concurrent gets on same non-tail key ====================

	/**
	 * "a" is not at tail. Two threads simultaneously call get("a"), both see
	 * NEEDS_MOVE and race for the write lock. The second must detect "a" is
	 * already at tail and skip the redundant move.
	 * <p>
	 * Expected: both return 1, and "a" is warmest afterwards.
	 */
	@Test
	void scenario4_needsMovePath_concurrentGetsOnSameNonTailKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("a", 1);
			dataStructure.put("b", 2);
			dataStructure.put("c", 3); // tail = "c", "a" is non-tail → NEEDS_MOVE

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

			done.await(5, TimeUnit.SECONDS);

			Assertions.assertEquals("a", dataStructure.getWarmest(),
					"After concurrent gets on 'a', 'a' should be warmest");

			dataStructure.remove("a");
			dataStructure.remove("b");
			dataStructure.remove("c");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent gets on same non-tail key produced incorrect results");
	}

	// ==================== Scenario 5: NEEDS_MOVE path — get() racing with put() (value update) ====================

	/**
	 * "key1" is not at tail. Thread A gets it (NEEDS_MOVE path).
	 * Thread B updates its value concurrently.
	 * <p>
	 * Expected: get() returns OLD_VALUE or NEW_VALUE — never anything else.
	 */
	@Test
	void scenario5_needsMovePath_getConcurrentWithPut() throws Exception {
		final int OLD_VALUE = 100;
		final int NEW_VALUE = 999;
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key1", OLD_VALUE);
			dataStructure.put("key2", 200); // makes "key1" non-tail → NEEDS_MOVE

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					Integer result = dataStructure.get("key1");
					if (result == null || (result != OLD_VALUE && result != NEW_VALUE)) {
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
					dataStructure.put("key1", NEW_VALUE);
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(5, TimeUnit.SECONDS);

			dataStructure.remove("key1");
			dataStructure.remove("key2");
		}

		Assertions.assertFalse(failed.get(),
				"NEEDS_MOVE get() concurrent with put() returned a corrupted value");
	}

	// ==================== Scenario 6: Concurrent put + remove on same key ====================

	/**
	 * Both put and remove are write operations. They must serialize correctly.
	 * Expected: no exceptions; final state is key-present (put won) or absent (remove won).
	 */
	@Test
	void scenario6_concurrentPutAndRemove_sameKey() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < ITERATIONS; i++) {
			dataStructure.put("key", 100);

			CyclicBarrier barrier = new CyclicBarrier(2);
			CountDownLatch done = new CountDownLatch(2);

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					dataStructure.put("key", 200);
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			executor.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					dataStructure.remove("key");
				} catch (Exception e) {
					failed.set(true);
				} finally {
					done.countDown();
				}
			});

			done.await(5, TimeUnit.SECONDS);

			Integer result = dataStructure.get("key");
			if (result != null && result != 200) {
				failed.set(true);
			}

			dataStructure.remove("key");
		}

		Assertions.assertFalse(failed.get(),
				"Concurrent put+remove produced an exception or corrupted value");
	}

	// ==================== Scenario 7: No deadlock — NEEDS_MOVE path under high contention ====================

	/**
	 * Many threads concurrently call get() on the same NON-TAIL key, all racing
	 * through the NEEDS_MOVE path (read lock → release → write lock for in-memory;
	 * serialized Lua script for Redis).
	 * <p>
	 * A deadlock manifests as a test timeout caught by the 30-second latch assertion.
	 */
	@Test
	void scenario7_noDeadlock_needsMovePathUnderHighContention() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);

		dataStructure.put("target", 42);
		dataStructure.put("other", 99); // "target" is non-tail

		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int t = 0; t < THREAD_COUNT; t++) {
			executor.submit(() -> {
				try {
					for (int i = 0; i < ITERATIONS; i++) {
						// Re-push "other" so "target" is always non-tail → NEEDS_MOVE fires
						dataStructure.put("other", 99);

						Integer result = dataStructure.get("target");
						if (result == null || result != 42) {
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

		boolean completed = done.await(60, TimeUnit.SECONDS);
		Assertions.assertTrue(completed, "Threads did not complete — possible DEADLOCK detected!");
		Assertions.assertFalse(failed.get(),
				"NEEDS_MOVE path under high contention returned wrong value");
	}

	// ==================== Scenario 8: Per-thread key isolation ====================

	/**
	 * Each thread owns its own key. Concurrent operations on different keys must
	 * not corrupt each other's data or the linked list structure.
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
						if (i == 0 && prev != null) failed.set(true);
						if (i > 0 && prev != null && prev != i - 1) failed.set(true);

						Integer got = dataStructure.get(myKey);
						if (got == null || got != i) failed.set(true);

						Integer removed = dataStructure.remove(myKey);
						if (removed == null || removed != i) failed.set(true);

						Integer afterRemove = dataStructure.get(myKey);
						if (afterRemove != null) failed.set(true);
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
				"Per-thread key operations produced incorrect results");
	}

	// ==================== Scenario 9: get() on non-existent key during heavy writes ====================

	/**
	 * get() on a key that is never inserted must always return null, even while
	 * other threads are writing at high frequency.
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
							if (result != null) failed.set(true);
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
				"get() on non-existent key returned non-null during heavy writes");
	}

	// ==================== Scenario 10: Warmest consistency after concurrent chaos ====================

	/**
	 * After many concurrent mixed operations, a single deterministic put() must
	 * set that key as warmest — proving the tail pointer is still correct.
	 */
	@Test
	void scenario10_warmestConsistencyAfterConcurrentChaos() throws Exception {
		AtomicBoolean failed = new AtomicBoolean(false);
		AtomicInteger errorCount = new AtomicInteger(0);

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
					errorCount.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
		}

		done.await(60, TimeUnit.SECONDS);
		Assertions.assertFalse(failed.get(),
				"Concurrent operations threw " + errorCount.get() + " exceptions");

		dataStructure.put("final-key", 9999);
		Assertions.assertEquals("final-key", dataStructure.getWarmest(),
				"After final put, warmest should be 'final-key'");
	}
}
