[MODE: INNOVATE]

# Innovation Ideas for WarmestDataStructure

Based on the research findings and clarified requirements, here are potential approaches for each part of the project.

---

## PART 1: WarmestDataStructure Implementation

### Approach A: HashMap + Doubly Linked List (LinkedHashMap-inspired)

**Idea**: Use a combination of two data structures:

1. **HashMap<String, Node>** - For O(1) key-value lookups
2. **Doubly Linked List** - For maintaining access/insertion order

**How it works**:

- Each node in the linked list contains the key, value, and pointers to prev/next
- HashMap maps keys directly to nodes
- On `put` or `get`: Move the node to the tail (most recent = warmest)
- On `remove`: Remove from both structures using HashMap for O(1) node lookup
- `getWarmest()`: Return the tail node's key

**Advantages**:

- True O(1) for all operations
- Similar to how `LinkedHashMap` with access-order works
- Well-understood pattern

**Disadvantages**:

- Slightly more complex than a simple map
- Need to manage doubly linked list manually for thread safety

---

### Approach B: HashMap + Stack with Lazy Cleanup

**Idea**:

1. **HashMap<String, Integer>** - For key-value storage
2. **Stack<String>** - For tracking "warmest" order

**How it works**:

- On `put` or `get`: Push key to stack
- On `getWarmest()`: Pop keys from stack until finding one that exists in HashMap
- On `remove`: Only remove from HashMap (lazy removal from stack)

**Advantages**:

- Simple implementation
- Amortized O(1) for most operations

**Disadvantages**:

- `getWarmest()` could be O(n) in worst case (not truly O(1))
- Stack grows unboundedly with repeated access to same key
- Does NOT meet the O(1) requirement

---

### Approach C: HashMap + TreeMap with Timestamp

**Idea**:

1. **HashMap<String, ValueWithTimestamp>** - Key to value + last access time
2. **TreeMap<Long, String>** - Timestamp to key for ordering

**Advantages**:

- Clear ordering semantics

**Disadvantages**:

- TreeMap operations are O(log n), not O(1)
- Does NOT meet the O(1) requirement

---

### **Recommended Approach: A (HashMap + Doubly Linked List)**

This is the only approach that guarantees O(1) for all operations.

---

## PART 2: REST API Design

### Approach A: Pure RESTful Resource-Based

**Endpoints**:

```
PUT    /data/{key}      - Body: {"value": 100}  → put(key, value)
GET    /data/{key}      - get(key)
DELETE /data/{key}      - remove(key)
GET    /data/warmest    - getWarmest()
```

**Advantages**:

- Follows REST conventions
- Intuitive for API consumers
- Supports HTTP caching semantics

**Disadvantages**:

- "warmest" could be confused with a key named "warmest"

---

### Approach B: RESTful with Sub-Resource

**Endpoints**:

```
PUT    /data/{key}      - Body: {"value": 100}
GET    /data/{key}
DELETE /data/{key}
GET    /warmest         - Separate endpoint for warmest
```

**Advantages**:

- Clear separation between data operations and warmest query
- No ambiguity with key names

**Disadvantages**:

- Slightly less cohesive API structure

---

### Approach C: RESTful with Query Parameter

**Endpoints**:

```
PUT    /data/{key}
GET    /data/{key}
GET    /data?warmest=true   - Alternative way to get warmest
DELETE /data/{key}
```

**Advantages**:

- Single resource concept

**Disadvantages**:

- Less intuitive
- Mixes concerns

---

### **Recommended Approach: B (RESTful with Sub-Resource)**

Clear separation, follows REST principles, no ambiguity.

---

## PART 3: Distributed Synchronization with Redis

### Approach A: Redis Hash + Redis List

**Idea**:

1. **Redis Hash** (`HSET`, `HGET`, `HDEL`) - For key-value storage (O(1))
2. **Redis List** (`LPUSH`, `LRANGE`, `LREM`) - For tracking order

**How it works**:

- `put`: HSET + move to front of list (LREM + LPUSH)
- `get`: HGET + move to front of list
- `remove`: HDEL + LREM
- `getWarmest`: LINDEX 0 (first element)

**Advantages**:

- Uses native Redis data structures
- Generally O(1) operations

**Disadvantages**:

- LREM is O(n) - does NOT meet O(1) requirement
- Not atomic without Lua scripts

---

### Approach B: Redis Hash + Redis Sorted Set with Timestamps

**Idea**:

1. **Redis Hash** - For key-value storage
2. **Redis Sorted Set** - Score = timestamp, member = key

**How it works**:

- `put`: HSET + ZADD with current timestamp
- `get`: HGET + ZADD with current timestamp
- `remove`: HDEL + ZREM
- `getWarmest`: ZREVRANGE 0 0 (highest score = most recent)

**Advantages**:

- Sorted set operations are O(log n) but very fast
- Atomic updates possible with MULTI/EXEC

**Disadvantages**:

- Sorted set operations are O(log n), not strictly O(1)
- However, for practical purposes this is extremely fast

---

### Approach C: Redis Hash + Custom Doubly Linked List in Redis

**Idea**: Implement the doubly linked list pattern in Redis:

1. **Redis Hash "data"** - `{key: value}`
2. **Redis Hash "nodes"** - `{key: {prev, next}}`
3. **Redis String "head"** and **"tail"**

**How it works**:

- Lua scripts for atomic operations
- Mimics the in-memory doubly linked list approach
- All operations O(1)

**Advantages**:

- True O(1) for all operations
- Atomic via Lua scripts
- Maintains strict insertion/access order

**Disadvantages**:

- More complex implementation
- Requires Lua scripting knowledge
- Multiple Redis commands per operation (wrapped in Lua)

---

### Approach D: Redis with Single Hash + Timestamp Field

**Idea**:

1. **Redis Hash** - Store `{key: {value, timestamp}}`
2. **Redis String** - Store current warmest key

**How it works**:

- `put/get`: Update hash and warmest key
- `remove`: Remove from hash, scan for new warmest if needed

**Advantages**:

- Simpler structure

**Disadvantages**:

- Finding new warmest after removal requires scan (O(n))
- Does NOT meet O(1) requirement

---

### **Recommended Approach: C (Custom Doubly Linked List in Redis with Lua)**

This is the only approach that guarantees true O(1) for all operations while maintaining order.

**Alternative consideration**: Approach B (Sorted Set) could be acceptable if O(log n) is considered "effectively O(1)"
for practical purposes, as Redis sorted sets are extremely optimized.

---

## Thread Safety Considerations

### For Part 2 (Single Instance)

**Approach A: Synchronized Methods**

- Simple `synchronized` keyword on interface methods
- Easy but coarse-grained locking

**Approach B: ReentrantReadWriteLock**

- Read operations can happen concurrently
- Write operations have exclusive access
- Better performance for read-heavy workloads

**Approach C: ConcurrentHashMap + AtomicReference**

- Lock-free for HashMap operations
- AtomicReference for linked list head/tail
- Most complex but best performance

---

### For Part 3 (Distributed)

**Approach A: Redis Transactions (MULTI/EXEC)**

- Groups commands atomically
- Simple but limited (no conditional logic)

**Approach B: Redis Lua Scripts**

- Full atomic execution
- Can implement complex logic
- Recommended for this use case

**Approach C: Distributed Locks (Redisson)**

- External library for distributed locking
- More overhead but flexible

---

## Docker Configuration Ideas

### Approach A: Single Docker Compose File

```yaml
services:
  redis:
    image: 'redis:latest'
    ports:
      - '6379:6379'

  app1:
    build: .
    ports:
      - '8080:8080'
    depends_on:
      - redis

  app2:
    build: .
    ports:
      - '8081:8080'
    depends_on:
      - redis

  app3:
    build: .
    ports:
      - '8082:8080'
    depends_on:
      - redis
```

**Advantages**:

- Single file deployment
- Easy to scale

---

### Approach B: Separate Development and Production Configs

- `compose.yaml` for development (single instance)
- `compose-prod.yaml` for 3-instance deployment

**Advantages**:

- Cleaner separation of concerns
- Easier local development

---

## Test Strategy Ideas

### Approach A: Single Test Class with Sequential Test Cases

- One test method that runs all 21 test cases in order
- Simple to implement, matches the document structure

### Approach B: Individual Test Methods

- Separate `@Test` method for each logical scenario
- Better isolation and error reporting
- Can use `@Order` annotation for dependencies

### Approach C: Parameterized Tests

- Use JUnit 5 parameterized tests for bulk operations
- Cleaner for similar test patterns

---

## Summary of Recommended Approaches

| Part          | Recommended Approach                                    |
|---------------|---------------------------------------------------------|
| Part 1        | HashMap + Doubly Linked List (LinkedHashMap-inspired)   |
| Part 2        | RESTful with Sub-Resource (`/data/{key}` + `/warmest`)  |
| Part 3        | Redis Custom Doubly Linked List with Lua Scripts        |
| Thread Safety | ReentrantReadWriteLock for local, Lua Scripts for Redis |
| Testing       | Individual test methods with `@Order` annotation        |

---

## Open Considerations

1. **Should there be a local implementation AND a Redis implementation?**
    - Possibility: Create an interface with two implementations, switch via Spring Profile
    - Possibility: Only Redis implementation from the start

2. **Error Handling**:
    - What should happen if Redis is unavailable?
    - Should there be fallback to local storage?

3. **API Response Format**:
    - Should responses be JSON objects (`{"value": 100}`) or plain values?
    - Should errors use standard HTTP status codes with problem details?

4. **Scalability Beyond 3 Instances**:
    - The Lua script approach scales to any number of instances
    - Should we consider Redis Cluster for high availability?
