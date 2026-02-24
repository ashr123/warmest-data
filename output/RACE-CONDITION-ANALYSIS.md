# Race Condition Analysis in WarmestDataStructure.get()

## The Problem

### Current Implementation:
```java
@Override
public Integer get(String key) {
    // STEP 1: Read lock
    lock.readLock().lock();
    Node node;
    try {
        node = map.get(key);
        if (node == null) {
            return null;
        }
        if (node == tail) {
            return node.value;  // ✅ Safe - still holding read lock
        }
    } finally {
        lock.readLock().unlock();
    }
    
    // ⚠️ RACE CONDITION WINDOW HERE! ⚠️
    // node reference is from read lock, but lock is released
    // Another thread could modify or remove this node!
    
    // STEP 2: Write lock
    lock.writeLock().lock();
    try {
        Node currentNode = map.get(key);  // ✅ Double-check
        if (currentNode != null && currentNode != tail) {
            moveToTail(currentNode);
            return currentNode.value;  // ✅ Safe - holding write lock
        } else if (currentNode != null) {
            return currentNode.value;  // ✅ Safe - holding write lock
        } else {
            return null;
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

## Race Condition Scenarios

### Scenario 1: Node Removed Between Locks ⚠️
```
Thread A (get "key1")          Thread B (remove "key1")
─────────────────────────────────────────────────────────
readLock()
node = map.get("key1") → Found
node != tail
readUnlock()
                               writeLock()
                               map.remove("key1")
                               detach(node)
                               writeUnlock()
writeLock()
currentNode = map.get("key1") → null  ✅ Handled correctly!
return null
writeUnlock()
```
**Verdict**: ✅ **Safe** - Double-check catches this

### Scenario 2: Node Moved to Tail Between Locks
```
Thread A (get "key1")          Thread B (get "key1")
─────────────────────────────────────────────────────────
readLock()
node = map.get("key1") → Found
node != tail
readUnlock()
                               readLock()
                               node2 = map.get("key1") → Found
                               node2 != tail
                               readUnlock()
                               
                               writeLock()
                               moveToTail(node2)
                               writeUnlock()
writeLock()
currentNode = map.get("key1") → Found
currentNode == tail  ✅ Detected!
return currentNode.value
writeUnlock()
```
**Verdict**: ✅ **Safe** - Double-check catches this

### Scenario 3: Reading node.value Without Lock ⚠️

**WAIT!** Looking at the code again more carefully:

In the optimized version, we read `node.value` while **still holding the read lock**:
```java
if (node == tail) {
    return node.value;  // ✅ Read lock still held!
}
```

And in the write lock section:
```java
return currentNode.value;  // ✅ Write lock held!
```

**Verdict**: ✅ **Safe** – We never read node.value without holding a lock!

## Actual Issue: The `node` Reference

The real concern is that we capture a `Node` reference under read lock, then use it after releasing the lock:

```java
Node node;
lock.readLock().lock();
try {
    node = map.get(key);  // Reference captured
    // ... checks ...
} finally {
    lock.readLock().unlock();
}

// node is now "orphaned" - no lock protects it!
// But we DON'T use it here - we re-fetch!

lock.writeLock().lock();
try {
    Node currentNode = map.get(key);  // ✅ Fresh fetch under write lock
    // We use currentNode, NOT the old node reference
}
```

**Verdict**: ✅ **Safe** – We don't use the orphaned reference

## The REAL Race Condition: Node Value Mutation

However, there **IS** a subtle race condition with the `node.value` field itself when we read it under read lock:

```java
lock.readLock().lock();
try {
    node = map.get(key);
    if (node == tail) {
        return node.value;  // ⚠️ What if another thread does put(key, newValue)?
    }
}
```

### Problematic Scenario:
```
Thread A (get "key1")          Thread B (put "key1", 999)
─────────────────────────────────────────────────────────
readLock()
node = map.get("key1") → node(value=100)
node == tail → true
                               writeLock()  ← BLOCKED by read lock
                               
return node.value → 100  ✅
readUnlock()
                               ← Now acquires write lock
                               node.value = 999
                               writeUnlock()
```

**Analysis**: This is actually **FINE**! 
- Thread A sees old value (100) - valid snapshot
- Thread B updates to new value (999)
- Both behaviors are acceptable in a concurrent system

### BUT... With ReentrantReadWriteLock:

**Read locks do NOT block other read locks**, but **write locks block everything**.

So the scenario is:
```
Thread A (get)                 Thread B (put)
─────────────────────────────────────────────────
readLock() ✅
node = map.get(key)
node.value = 100
                               writeLock() ❌ BLOCKED!
                               (Read lock held by A)
return 100
readUnlock()
                               ← Now acquires write lock ✅
                               node.value = 999
```

**Verdict**: ✅ **Safe** - Write lock waits for read lock to release

## Conclusion: Is The Current Implementation Safe?

### Answer: ✅ **YES, it's safe!**

**Reasons:**
1. ✅ Double-check pattern correctly re-fetches node under write lock
2. ✅ Never use orphaned node references
3. ✅ Read lock prevents writes from occurring during reads
4. ✅ All value reads happen while holding appropriate lock

### The Implementation is Correct Because:

1. **Read Lock Phase**:
   - Checks if key exists
   - Checks if already at tail
   - Returns value **while still holding read lock** (safe!)
   - If modification needed, releases lock and proceeds to write lock

2. **Write Lock Phase**:
   - Re-fetches node (double-check)
   - Verifies node still exists and needs moving
   - Performs modification
   - Returns value **while holding write lock** (safe!)

3. **Lock Properties**:
   - Read lock allows multiple concurrent readers
   - Write lock is exclusive (blocks readers and writers)
   - No value is read without holding a lock

### Summary Table:

| Concern                    | Status | Reason                                |
|----------------------------|--------|---------------------------------------|
| Node removed between locks | ✅ Safe | Double-check detects removal          |
| Node moved between locks   | ✅ Safe | Double-check detects movement         |
| Value read without lock    | ✅ Safe | Always read under lock                |
| Orphaned reference usage   | ✅ Safe | Never used, re-fetch under write lock |
| Concurrent value updates   | ✅ Safe | Write lock waits for read lock        |

## Recommendation

The current implementation is **correct and safe**. No changes needed for correctness.

However, if you want to be extra defensive, you could add assertions:

```java
// In write lock section:
assert lock.isWriteLockedByCurrentThread() : "Must hold write lock";
```

But this is **not necessary** – the code is already safe.

---

## Race Condition Test Coverage

All scenarios identified above are now verified by dedicated concurrency test suites for both profiles:

### Test Architecture

All 10 race condition scenarios are defined once in a shared abstract base class:

- **`AbstractRaceConditionTest.java`** — abstract base with all 10 `@Test` methods and `@Autowired WarmestDataStructureInterface`
- **`WarmestDataStructureRaceConditionTest.java`** — extends base, `@SpringBootTest` with default profile (in-memory)
- **`RedisWarmestDataStructureRaceConditionTest.java`** — extends base, `@ActiveProfiles("redis")` + Testcontainers

### Scenarios Tested (10 per profile)

| #  | Scenario                                                   | What It Verifies                                                             |
|----|------------------------------------------------------------|------------------------------------------------------------------------------|
| 1  | AT_TAIL: concurrent `get` + `put` on same tail key         | The bug the old code had — `node.value` read without lock                    |
| 2  | AT_TAIL: concurrent `get` + `remove` on same tail key      | Node removed while fast-path read is in progress                             |
| 3  | NEEDS_MOVE: concurrent `get` + `remove` on non-tail key    | Double-check under write lock catches removal                                |
| 4  | NEEDS_MOVE: concurrent `get` + `get` on same non-tail key  | Second thread detects node already at tail after first moved it              |
| 5  | NEEDS_MOVE: concurrent `get` + `put` on non-tail key       | Value mutation; returns valid snapshot (old or new value)                     |
| 6  | Concurrent `put` + `remove` on same key                    | No exceptions or data corruption from conflicting write operations           |
| 7  | NEEDS_MOVE path under high contention (10 threads)         | No deadlock/livelock from read-lock → write-lock upgrade pattern             |
| 8  | Per-thread key consistency (put-get-remove cycles)         | Isolated key operations return expected values under concurrency             |
| 9  | `get` non-existent key during heavy writes                 | Always returns null; no cross-contamination from concurrent writes           |
| 10 | Warmest tracking after concurrent chaos                    | Final deterministic `put` correctly sets warmest after concurrent ops        |

### Test Configuration

- **Thread count**: 10 concurrent threads
- **Iterations**: 1 000 per scenario for both profiles
- **Synchronization**: `CyclicBarrier` for precise concurrent launch, `CountDownLatch` for completion tracking
- **Deadlock detection**: 60-second timeout assertions on thread completion
