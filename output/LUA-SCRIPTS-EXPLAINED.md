# Why Lua Scripts Are Atomic and O(1)

## Executive Summary

The 4 Lua scripts (`get.lua`, `put.lua`, `remove.lua`, `getWarmest.lua`) achieve:

1. **Atomicity**: Through Redis's Lua execution model
2. **O(1) Complexity**: Through hash-based direct access

---

## PART 1: Why Are They Atomic?

### Redis Lua Execution Model

Redis provides **strong atomicity guarantees** for Lua scripts:

```
┌─────────────────────────────────────────────────────┐
│  Redis Server (Single-threaded execution)          │
│                                                     │
│  ┌───────────────────────────────────────────┐     │
│  │  Lua Script Execution                     │     │
│  │  - Blocks all other commands              │     │
│  │  - No interruptions possible              │     │
│  │  - Runs to completion                     │     │
│  └───────────────────────────────────────────┘     │
│                                                     │
│  Other commands wait in queue                      │
└─────────────────────────────────────────────────────┘
```

### Key Atomicity Guarantees

1. **No Concurrent Execution**
    - Redis is single-threaded
    - When a Lua script runs, NO other command can execute
    - Other clients are blocked until script completes

2. **Script Execution is Transactional**
    - Either ALL operations in the script succeed
    - Or NONE of them do (if script errors)
    - No partial state changes visible to other clients

3. **Visibility**
    - All changes are visible atomically at script completion
    - No intermediate states are observable
    - Other clients see the "before" or "after" state, never "during"

### Example: put.lua Atomicity

```lua
-- This entire sequence is ATOMIC:
local previousValue = redis.call('HGET', dataKey, key)       -- Read previous value
redis.call('HSET', dataKey, key, value)                      -- Update value
-- detach(key) function executes atomically:
    local prevNode = redis.call('HGET', prevKey, key)        -- Read prev pointer
    local nextNode = redis.call('HGET', nextKey, key)        -- Read next pointer
    redis.call('HSET', nextKey, prevNode, nextNode)          -- Update pointers
    redis.call('HSET', prevKey, nextNode, prevNode)          -- Update pointers
-- attachToTail(key) function executes atomically:
    local currentTail = redis.call('GET', tailKey)           -- Read tail
    redis.call('HSET', nextKey, currentTail, key)            -- Update pointers
    redis.call('HSET', prevKey, key, currentTail)            -- Update pointers
    redis.call('SET', tailKey, key)                          -- Update tail
```

**Without Lua**: Each redis.call() would be a separate command → race conditions possible
**With Lua**: All operations execute as one atomic unit → NO race conditions

### Why This Matters for Multi-Instance Deployment

```
Instance 1                 Redis                Instance 2
    |                        |                       |
    |----PUT key=a val=1---->|                       |
    |                        |<----GET key=a---------|
    |                        |                       |
    |                     [ATOMIC]                   |
    |                   put.lua runs                 |
    |                   ALL changes                  |
    |                    applied                     |
    |                        |                       |
    |<------return null------|                       |
    |                        |-----return 1--------->|
```

Both instances see **consistent state** - no partial updates.

---

## PART 2: Why Are They O(1)?

### Understanding the Data Structure

The implementation uses **4 Redis keys** to maintain a doubly linked list:

```
Data Structure in Redis:
┌──────────────────────────────────────────────────────┐
│ warmest:data (Hash)   →  {"a": "100", "b": "200"}   │
│ warmest:prev (Hash)   →  {"b": "a"}                 │
│ warmest:next (Hash)   →  {"a": "b"}                 │
│ warmest:tail (String) →  "b"                         │
└──────────────────────────────────────────────────────┘

Linked list visualization:
                              tail
                               ↓
   [a:100] ←→ [b:200]
   prev=null  prev=a
   next=b     next=null
```

### O(1) Operations Analysis

#### 1. getWarmest.lua - O(1)

```lua
local tail = redis.call('GET', tailKey)
return tail
```

**Complexity Breakdown**:

- `GET tailKey` → **O(1)** (Redis string GET is O(1))
- **Total: O(1)**

**Why O(1)**:

- Direct key access
- No iteration required
- No searching

---

#### 2. get.lua - O(1)

```lua
-- Step 1: Get value
local value = redis.call('HGET', dataKey, key)  -- O(1)

-- Step 2: Get current tail
local currentTail = redis.call('GET', tailKey)   -- O(1)

-- Step 3: Get node pointers
local prevNode = redis.call('HGET', prevKey, key)  -- O(1)
local nextNode = redis.call('HGET', nextKey, key)  -- O(1)

-- Step 4: Update pointers (detach and reattach)
redis.call('HSET', nextKey, prevNode, nextNode)  -- O(1)
redis.call('HSET', prevKey, nextNode, prevNode)  -- O(1)
redis.call('HSET', nextKey, currentTail, key)    -- O(1)
redis.call('HSET', prevKey, key, currentTail)    -- O(1)
redis.call('HDEL', nextKey, key)                 -- O(1)
redis.call('SET', tailKey, key)                  -- O(1)
```

**Complexity Breakdown**:
| Operation | Redis Command | Complexity | Reason |
|-----------|---------------|------------|--------|
| Get value | HGET | O(1) | Hash field access |
| Get tail | GET | O(1) | String key access |
| Get prev pointer | HGET | O(1) | Hash field access |
| Get next pointer | HGET | O(1) | Hash field access |
| Update 4 pointers | HSET × 4 | O(1) each | Hash field update |
| Delete pointer | HDEL | O(1) | Hash field deletion |
| Update tail | SET | O(1) | String key update |

**Total Operations**: ~10 operations, ALL O(1)
**Total Complexity**: O(1) (constant number of O(1) operations)

**Key Insight**:

- We access nodes **directly by key** using hash lookups
- No iteration through the list
- No searching for nodes
- All pointer updates are direct hash field updates

---

#### 3. put.lua - O(1)

```lua
-- Check if key exists
local previousValue = redis.call('HGET', dataKey, key)  -- O(1)

if exists then
    -- Update value
    redis.call('HSET', dataKey, key, value)  -- O(1)
    
    -- Get pointers (same as get.lua)
    local prevNode = redis.call('HGET', prevKey, key)    -- O(1)
    local nextNode = redis.call('HGET', nextKey, key)    -- O(1)
    local currentTail = redis.call('GET', tailKey)       -- O(1)
    
    -- Detach and reattach (6-8 operations, all O(1))
    -- [Same pointer manipulation as get.lua]
else
    -- New key
    redis.call('HSET', dataKey, key, value)              -- O(1)
    local currentTail = redis.call('GET', tailKey)       -- O(1)
    redis.call('HSET', nextKey, currentTail, key)        -- O(1)
    redis.call('HSET', prevKey, key, currentTail)        -- O(1)
    redis.call('SET', tailKey, key)                      -- O(1)
end
```

**Complexity Breakdown**:

- All operations are hash field access/update: O(1)
- Worst case: ~15 operations, all O(1)
- **Total: O(1)**

---

#### 4. remove.lua - O(1)

```lua
-- Get value and position
local value = redis.call('HGET', dataKey, key)      -- O(1)
local prevNode = redis.call('HGET', prevKey, key)   -- O(1)
local nextNode = redis.call('HGET', nextKey, key)   -- O(1)

-- Remove from data
redis.call('HDEL', dataKey, key)                    -- O(1)

-- Update pointers (detach)
redis.call('HSET', nextKey, prevNode, nextNode)     -- O(1)
redis.call('HSET', prevKey, nextNode, prevNode)     -- O(1)
-- ... (more pointer updates, all O(1))

-- Clean up
redis.call('HDEL', prevKey, key)                    -- O(1)
redis.call('HDEL', nextKey, key)                    -- O(1)
```

**Complexity Breakdown**:

- All operations are hash field access/update/delete: O(1)
- Worst case: ~12 operations, all O(1)
- **Total: O(1)**

---

## Why NOT O(n)?

### Common Misconception: "Linked Lists Are O(n)"

**Traditional Linked List** (e.g., Java's LinkedList):

```java
// To remove node with key "x":
Node current = head;
while (current != null) {           // O(n) - must traverse
    if (current.key.equals("x")) {
        remove(current);             // O(1) - once found
        break;
    }
    current = current.next;
}
// Total: O(n)
```

**Our Redis Implementation**:

```lua
-- To remove node with key "x":
local prevNode = redis.call('HGET', prevKey, "x")  -- O(1) - direct access!
local nextNode = redis.call('HGET', nextKey, "x")  -- O(1) - direct access!
-- Update pointers: O(1)
-- Total: O(1)
```

### The Secret: Hash-Based Node References

```
Traditional Doubly Linked List:
  head → [Node] → [Node] → [Node] → tail
         ↑
         To access, must traverse from head
         Complexity: O(n)

Our Redis Implementation:
  HashMap: {"a" → Node_a, "b" → Node_b, "c" → Node_c}
           ↑
           Direct access by key
           Complexity: O(1)

  Plus linked list for order:
  head → [a] ↔ [b] ↔ [c] ← tail
```

We maintain **TWO data structures**:

1. **HashMap** (warmest:prev, warmest:next, warmest:data) → O(1) access
2. **Doubly Linked List** (head/tail pointers + prev/next) → O(1) order maintenance

This is exactly like Java's `LinkedHashMap`!

---

## Complexity Comparison Table

| Operation         | Traditional Linked List | Our Redis Implementation                 | Why?               |
|-------------------|-------------------------|------------------------------------------|--------------------|
| **get(key)**      | O(n) - must search      | **O(1)** - hash lookup                   | Hash access        |
| **put(key, val)** | O(n) - must search      | **O(1)** - hash lookup + pointer updates | Hash access        |
| **remove(key)**   | O(n) - must search      | **O(1)** - hash lookup + pointer updates | Hash access        |
| **getWarmest()**  | O(1) - tail reference   | **O(1)** - tail reference                | Direct tail access |

---

## Redis Command Complexity Reference

All Redis commands used are O(1):

| Command                | Complexity | Reason                        |
|------------------------|------------|-------------------------------|
| `HGET key field`       | O(1)       | Hash table lookup             |
| `HSET key field value` | O(1)       | Hash table insertion          |
| `HDEL key field`       | O(1)       | Hash table deletion           |
| `GET key`              | O(1)       | Key-value lookup              |
| `SET key value`        | O(1)       | Key-value insertion           |
| `DEL key`              | O(1)       | Key deletion (for single key) |

**Source**: [Redis Command Reference](https://redis.io/commands/)

---

## Performance Proof by Execution Count

### get.lua Execution Analysis

```
Line-by-line complexity:
──────────────────────────────────────────────────
16: local value = redis.call('HGET', ...)      → O(1)
18: if value == false then                     → O(1)
23: local currentTail = redis.call('GET', ...) → O(1)
25: if currentTail ~= key then                 → O(1)
26:     local prevNode = redis.call('HGET'...) → O(1)
27:     local nextNode = redis.call('HGET'...) → O(1)
30-35:  Detach logic (4-5 operations)          → O(1) each
47-51:  Attach logic (4 operations)            → O(1) each
58: return value                               → O(1)
──────────────────────────────────────────────────
Total: ~15 operations
Each operation: O(1)
Total complexity: O(15) = O(1)
```

**Mathematical Proof**:

```
T(n) = c₁ + c₂ + c₃ + ... + c₁₅
     = Σ(constants)
     = constant
     = O(1)
```

Where n = number of keys in the system (doesn't appear in the formula!)

---

## Why This Design Works

### 1. **Trade Space for Time**

- Uses 5 Redis keys instead of 1
- Extra space: O(n) for pointers
- Time saved: O(n) → O(1) for operations

### 2. **Hash Tables Are Magic**

- Hash table lookup: O(1) average case
- Redis hashes are optimized hash tables
- Direct key access eliminates traversal

### 3. **Pointer Manipulation**

- Doubly linked list detach/attach: O(1)
- Why? We have the node reference already!
- No searching needed

### 4. **Lua Guarantees Atomicity**

- All O(1) operations happen atomically
- No race conditions
- Consistent state across instances

---

## Real-World Implications

### Scalability

```
Performance vs. Data Size:

Traditional LinkedList:
  100 keys    → 50 operations avg (O(n/2))
  1,000 keys  → 500 operations avg
  10,000 keys → 5,000 operations avg
  ❌ Linear degradation

Our Redis Implementation:
  100 keys    → ~15 operations (O(1))
  1,000 keys  → ~15 operations (O(1))
  10,000 keys → ~15 operations (O(1))
  ✅ Constant performance
```

### Throughput

With O(1) operations:

- 1,000 requests/sec sustainable
- 10,000 requests/sec sustainable
- Performance independent of data size
- Limited only by Redis throughput (50k-100k ops/sec)

---

## Summary

### Atomicity: WHY?

1. **Redis single-threaded execution** → No concurrent script execution
2. **Lua script runs to completion** → No interruptions
3. **All-or-nothing semantics** → Transaction-like behavior
4. **Multi-instance safe** → Consistent state across all clients

### O(1) Complexity: HOW?

1. **Hash-based node access** → No traversal needed
2. **Direct key lookup** → `HGET` instead of iteration
3. **Fixed number of operations** → Independent of data size
4. **Constant pointer updates** → Only touching 2-3 nodes max

### The Winning Combination

```
Atomicity (Lua) + O(1) (Hash Tables) = Fast & Safe Distributed System
```

This design allows:

- ✅ Multiple app instances
- ✅ Thousands of requests/second
- ✅ No race conditions
- ✅ Predictable performance
- ✅ Horizontal scalability

**That's why these Lua scripts are both atomic AND O(1)!** 🚀
