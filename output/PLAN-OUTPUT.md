[MODE: PLAN]

# Technical Specification: WarmestDataStructure Implementation

---

## Analysis: Can We Use Java's Built-in LinkedList?

### Java's `java.util.LinkedList` Limitations

Java's built-in `LinkedList` is a **doubly linked list**, but it does **NOT** provide O(1) access to arbitrary nodes:

| Operation           | LinkedList Method | Complexity |
|---------------------|-------------------|------------|
| Add to end          | `addLast()`       | O(1) ✅     |
| Remove from end     | `removeLast()`    | O(1) ✅     |
| Remove by reference | ❌ Not available   | -          |
| Remove by value     | `remove(Object)`  | O(n) ❌     |
| Remove by index     | `remove(int)`     | O(n) ❌     |
| Get last element    | `getLast()`       | O(1) ✅     |

**The Problem**: When we need to move an existing key to the tail (on `put` or `get`), we must:

1. Find the node in the list → O(n) with built-in LinkedList
2. Remove it → O(1) if we have the node reference, but we don't
3. Add to tail → O(1)

**Conclusion**: Java's built-in `LinkedList` **cannot be used** for O(1) operations because it doesn't expose node
references. We must implement a **custom doubly linked list** where:

- HashMap stores references to actual Node objects
- Node removal is O(1) given the node reference

---

## PART 1: WarmestDataStructure Implementation

### File Structure

```
src/main/java/io/github/ashr123/warmestdata/
├── WarmestDataStructureInterface.java    (NEW - Interface)
├── WarmestDataStructure.java             (NEW - Thread-safe implementation)
├── WarmestDataApplication.java           (EXISTS - No changes)
```

### 1.1 Interface Definition

**File**: `src/main/java/io/github/ashr123/warmestdata/WarmestDataStructureInterface.java`

```java
package io.github.ashr123.warmestdata;

public interface WarmestDataStructureInterface {
	Integer put(String key, int value);

	Integer remove(String key);

	Integer get(String key);

	String getWarmest();
}
```

### 1.2 Implementation with Custom Doubly Linked List

**File**: `src/main/java/io/github/ashr123/warmestdata/WarmestDataStructure.java`

#### Inner Class: Node

```java
private static class Node {
	String key;
	int value;
	Node prev;
	Node next;

	Node(String key, int value) {
		this.key = key;
		this.value = value;
	}
}
```

#### Fields

```java
private final Map<String, Node> map = new HashMap<>();
private Node head = null;  // Oldest (coldest)
private Node tail = null;  // Newest (warmest)
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
```

#### Method Specifications

**`put(String key, int value)`**:

1. Acquire write lock
2. If key exists in map:
    - Get existing node
    - Store previous value
    - Update node's value
    - Move node to tail (detach + attach to tail)
    - Return previous value
3. If key doesn't exist:
    - Create new node
    - Add to map
    - Attach to tail
    - Return null
4. Release write lock

**`get(String key)`**:

1. Acquire write lock (because we modify order)
2. If key exists in map:
    - Get node
    - Move node to tail
    - Return value
3. If key doesn't exist:
    - Return null
4. Release write lock

**`remove(String key)`**:

1. Acquire write lock
2. If key exists in map:
    - Remove from map
    - Detach node from linked list
    - Return value
3. If key doesn't exist:
    - Return null
4. Release write lock

**`getWarmest()`**:

1. Acquire read lock
2. If tail is null, return null
3. Return tail.key
4. Release read lock

#### Helper Methods

**`moveToTail(Node node)`**:

1. If node is already tail, do nothing
2. Detach node from current position
3. Attach node to tail

**`detach(Node node)`**:

1. If node.prev != null: node.prev.next = node.next
2. Else: head = node.next
3. If node.next != null: node.next.prev = node.prev
4. Else: tail = node.prev
5. Clear node.prev and node.next

**`attachToTail(Node node)`**:

1. node.prev = tail
2. node.next = null
3. If tail != null: tail.next = node
4. tail = node
5. If head == null: head = node

---

## PART 2: REST API Implementation

### File Structure

```
src/main/java/io/github/ashr123/warmestdata/
├── controller/
│   └── WarmestDataController.java        (NEW)
├── dto/
│   ├── WarmestDataStructureInterface.java (FROM PART 1)
│   └── WarmestDataStructure.java          (FROM PART 1 - add @Profile("!redis"))
```

### 2.1 Configuration

Configuration is handled via `@Profile` annotations:
- `WarmestDataStructure` uses `@Service` and `@Profile("!redis")` annotations
- This allows Spring to automatically provide the bean when Redis profile is not active
- No separate `@Configuration` class is needed

### 2.2 REST Controller

**File**: `src/main/java/io/github/ashr123/warmestdata/controller/WarmestDataController.java`

#### Endpoints

| HTTP Method | Path          | Description     | Request Body              | Response          |
|-------------|---------------|-----------------|---------------------------|-------------------|
| PUT         | `/data/{key}` | Put key-value   | Raw integer (e.g., `100`) | Integer or `null` |
| GET         | `/data/{key}` | Get value       | -                         | Integer or 404    |
| DELETE      | `/data/{key}` | Remove key      | -                         | Integer or `null` |
| GET         | `/warmest`    | Get warmest key | -                         | String or `null`  |

#### Controller Implementation

```java

@RestController
public class WarmestDataController {

	private final WarmestDataStructureInterface dataStructure;

	public WarmestDataController(WarmestDataStructureInterface dataStructure) {
		this.dataStructure = dataStructure;
	}

	@PutMapping("/data/{key}")
	public ResponseEntity<Integer> put(@PathVariable String key, @RequestBody int value) {
		return ResponseEntity.ok(dataStructure.put(key, value));
	}

	@GetMapping("/data/{key}")
	public ResponseEntity<Integer> get(@PathVariable String key) {
		Integer value = dataStructure.get(key);
		return value == null ?
				ResponseEntity.notFound().build() :
				ResponseEntity.ok(value);
	}

	@DeleteMapping("/data/{key}")
	public ResponseEntity<Integer> remove(@PathVariable String key) {
		return ResponseEntity.ok(dataStructure.remove(key));
	}

	@GetMapping("/warmest")
	public ResponseEntity<String> getWarmest() {
		return ResponseEntity.ok(dataStructure.getWarmest());
	}
}
```

**Benefits of this approach:**

- Direct serialization of primitive types eliminates boilerplate
- No need for wrapper DTOs (`ValueRequest`, `ValueResponse`)
- Simpler client API (send raw integers)
- Reduced JSON nesting
- Spring's built-in support for primitive type serialization

---

## PART 3: Redis Implementation with Lua Scripts

### File Structure

```
src/main/java/io/github/ashr123/warmestdata/
├── redis/
│   └── RedisWarmestDataStructure.java    (NEW)
├── config/
│   └── WarmestDataConfig.java            (MODIFY - Add Redis bean)
│   └── RedisConfig.java                  (NEW)
src/main/resources/
├── scripts/
│   └── put.lua                           (NEW)
│   └── get.lua                           (NEW)
│   └── remove.lua                        (NEW)
│   └── getWarmest.lua                    (NEW)
├── application.properties                (MODIFY)
```

### 3.1 Redis Data Structure Design

**Keys Used**:

- `warmest:data` - Hash storing key → value
- `warmest:prev` - Hash storing key → previous_key
- `warmest:next` - Hash storing key → next_key
- `warmest:head` - String storing head key
- `warmest:tail` - String storing tail key (warmest)

### 3.2 Lua Scripts

**File**: `src/main/resources/scripts/put.lua`

```lua
-- KEYS[1] = "warmest:data"
-- KEYS[2] = "warmest:prev"
-- KEYS[3] = "warmest:next"
-- KEYS[4] = "warmest:head"
-- KEYS[5] = "warmest:tail"
-- ARGV[1] = key
-- ARGV[2] = value

local dataKey = KEYS[1]
local prevKey = KEYS[2]
local nextKey = KEYS[3]
local headKey = KEYS[4]
local tailKey = KEYS[5]
local key = ARGV[1]
local value = ARGV[2]

-- Get previous value
local previousValue = redis.call('HGET', dataKey, key)

-- Check if key exists
local exists = previousValue ~= false

if exists then
    -- Key exists: update value and move to tail
    redis.call('HSET', dataKey, key, value)
    
    -- Get current position
    local prevNode = redis.call('HGET', prevKey, key)
    local nextNode = redis.call('HGET', nextKey, key)
    local currentTail = redis.call('GET', tailKey)
    
    -- If already tail, nothing to do for position
    if currentTail ~= key then
        -- Detach from current position
        if prevNode and prevNode ~= false then
            if nextNode and nextNode ~= false then
                redis.call('HSET', nextKey, prevNode, nextNode)
            else
                redis.call('HDEL', nextKey, prevNode)
            end
        else
            -- This was head
            if nextNode and nextNode ~= false then
                redis.call('SET', headKey, nextNode)
            end
        end
        
        if nextNode and nextNode ~= false then
            if prevNode and prevNode ~= false then
                redis.call('HSET', prevKey, nextNode, prevNode)
            else
                redis.call('HDEL', prevKey, nextNode)
            end
        end
        
        -- Attach to tail
        if currentTail and currentTail ~= false then
            redis.call('HSET', nextKey, currentTail, key)
        end
        redis.call('HSET', prevKey, key, currentTail)
        redis.call('HDEL', nextKey, key)
        redis.call('SET', tailKey, key)
    end
else
    -- Key doesn't exist: create and add to tail
    redis.call('HSET', dataKey, key, value)
    
    local currentTail = redis.call('GET', tailKey)
    local currentHead = redis.call('GET', headKey)
    
    if currentTail and currentTail ~= false then
        redis.call('HSET', nextKey, currentTail, key)
        redis.call('HSET', prevKey, key, currentTail)
    end
    
    redis.call('SET', tailKey, key)
    
    if not currentHead or currentHead == false then
        redis.call('SET', headKey, key)
    end
end

return previousValue
```

**File**: `src/main/resources/scripts/get.lua`

```lua
-- KEYS[1] = "warmest:data"
-- KEYS[2] = "warmest:prev"
-- KEYS[3] = "warmest:next"
-- KEYS[4] = "warmest:head"
-- KEYS[5] = "warmest:tail"
-- ARGV[1] = key

local dataKey = KEYS[1]
local prevKey = KEYS[2]
local nextKey = KEYS[3]
local headKey = KEYS[4]
local tailKey = KEYS[5]
local key = ARGV[1]

-- Get value
local value = redis.call('HGET', dataKey, key)

if value == false then
    return nil
end

-- Move to tail (same logic as put for existing key)
local currentTail = redis.call('GET', tailKey)

if currentTail ~= key then
    local prevNode = redis.call('HGET', prevKey, key)
    local nextNode = redis.call('HGET', nextKey, key)
    
    -- Detach
    if prevNode and prevNode ~= false then
        if nextNode and nextNode ~= false then
            redis.call('HSET', nextKey, prevNode, nextNode)
        else
            redis.call('HDEL', nextKey, prevNode)
        end
    else
        if nextNode and nextNode ~= false then
            redis.call('SET', headKey, nextNode)
        end
    end
    
    if nextNode and nextNode ~= false then
        if prevNode and prevNode ~= false then
            redis.call('HSET', prevKey, nextNode, prevNode)
        else
            redis.call('HDEL', prevKey, nextNode)
        end
    end
    
    -- Attach to tail
    if currentTail and currentTail ~= false then
        redis.call('HSET', nextKey, currentTail, key)
    end
    redis.call('HSET', prevKey, key, currentTail)
    redis.call('HDEL', nextKey, key)
    redis.call('SET', tailKey, key)
end

return value
```

**File**: `src/main/resources/scripts/remove.lua`

```lua
-- KEYS[1] = "warmest:data"
-- KEYS[2] = "warmest:prev"
-- KEYS[3] = "warmest:next"
-- KEYS[4] = "warmest:head"
-- KEYS[5] = "warmest:tail"
-- ARGV[1] = key

local dataKey = KEYS[1]
local prevKey = KEYS[2]
local nextKey = KEYS[3]
local headKey = KEYS[4]
local tailKey = KEYS[5]
local key = ARGV[1]

-- Get value
local value = redis.call('HGET', dataKey, key)

if value == false then
    return nil
end

-- Remove from data hash
redis.call('HDEL', dataKey, key)

-- Get position
local prevNode = redis.call('HGET', prevKey, key)
local nextNode = redis.call('HGET', nextKey, key)

-- Detach
if prevNode and prevNode ~= false then
    if nextNode and nextNode ~= false then
        redis.call('HSET', nextKey, prevNode, nextNode)
    else
        redis.call('HDEL', nextKey, prevNode)
    end
else
    -- Was head
    if nextNode and nextNode ~= false then
        redis.call('SET', headKey, nextNode)
    else
        redis.call('DEL', headKey)
    end
end

if nextNode and nextNode ~= false then
    if prevNode and prevNode ~= false then
        redis.call('HSET', prevKey, nextNode, prevNode)
    else
        redis.call('HDEL', prevKey, nextNode)
    end
else
    -- Was tail
    if prevNode and prevNode ~= false then
        redis.call('SET', tailKey, prevNode)
    else
        redis.call('DEL', tailKey)
    end
end

-- Clean up node references
redis.call('HDEL', prevKey, key)
redis.call('HDEL', nextKey, key)

return value
```

**File**: `src/main/resources/scripts/getWarmest.lua`

```lua
-- KEYS[1] = "warmest:tail"

local tailKey = KEYS[1]
local tail = redis.call('GET', tailKey)

if tail == false then
    return nil
end

return tail
```

### 3.3 Redis Implementation Class

**File**: `src/main/java/io/github/ashr123/warmestdata/redis/RedisWarmestDataStructure.java`

```java

@Component
@Profile("redis")
public class RedisWarmestDataStructure implements WarmestDataStructureInterface {

	private static final String DATA_KEY = "warmest:data";
	private static final String PREV_KEY = "warmest:prev";
	private static final String NEXT_KEY = "warmest:next";
	private static final String HEAD_KEY = "warmest:head";
	private static final String TAIL_KEY = "warmest:tail";

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<String> putScript;
	private final RedisScript<String> getScript;
	private final RedisScript<String> removeScript;
	private final RedisScript<String> getWarmestScript;

	// Constructor with script loading
	// Methods calling scripts via redisTemplate.execute()
}
```

### 3.4 Configuration Updates

**File**: `src/main/java/io/github/ashr123/warmestdata/config/WarmestDataConfig.java`

```java

@Configuration
public class WarmestDataConfig {

	@Bean
	@Profile("!redis")
	public WarmestDataStructureInterface localWarmestDataStructure() {
		return new WarmestDataStructure();
	}
}
```

**File**: `src/main/java/io/github/ashr123/warmestdata/config/RedisConfig.java`

```java

@Configuration
@Profile("redis")
public class RedisConfig {

	@Bean
	public RedisScript<String> putScript() {
		return RedisScript.of(new ClassPathResource("scripts/put.lua"), String.class);
	}

	// Similar beans for other scripts
}
```

**File**: `src/main/resources/application.properties`

```properties
spring.application.name=warmest-data
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

### 3.5 Docker Compose for 3 Instances

**File**: `compose.yaml` (MODIFY)

```yaml
services:
  redis:
    image: 'redis:latest'
    ports:
      - '6379:6379'
```

**File**: `compose-multi.yaml` (NEW - for 3 instance deployment)

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
    environment:
      - SPRING_PROFILES_ACTIVE=redis
      - REDIS_HOST=redis
    depends_on:
      - redis

  app2:
    build: .
    ports:
      - '8081:8080'
    environment:
      - SPRING_PROFILES_ACTIVE=redis
      - REDIS_HOST=redis
    depends_on:
      - redis

  app3:
    build: .
    ports:
      - '8082:8080'
    environment:
      - SPRING_PROFILES_ACTIVE=redis
      - REDIS_HOST=redis
    depends_on:
      - redis
```

**File**: `Dockerfile` (NEW)

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## PART 4: Testing

### File Structure

```
src/test/java/io/github/ashr123/warmestdata/
├── WarmestDataStructureTest.java         (NEW - Unit tests for Part 1)
├── WarmestDataControllerTest.java        (NEW - Integration tests for Part 2)
├── RedisWarmestDataStructureTest.java    (NEW - Integration tests for Part 3)
├── TestcontainersConfiguration.java      (EXISTS)
├── WarmestDataApplicationTests.java      (EXISTS - MODIFY)
```

### 4.1 Unit Tests for WarmestDataStructure

**File**: `src/test/java/io/github/ashr123/warmestdata/WarmestDataStructureTest.java`

All 21 test cases from the document, organized as individual `@Test` methods with `@Order` annotation.

Test method naming convention:

- `test01_getWarmest_whenEmpty_returnsNull()`
- `test02_put_whenNewKey_returnsNull()`
- `test03_getWarmest_afterPut_returnsKey()`
- ... etc.

### 4.2 Controller Integration Tests

**File**: `src/test/java/io/github/ashr123/warmestdata/WarmestDataControllerTest.java`

Using `@WebMvcTest` with mocked `WarmestDataStructureInterface`.

Tests:

- PUT /data/{key} returns previous value
- GET /data/{key} returns value or 404
- DELETE /data/{key} returns previous value
- GET /warmest returns warmest key

### 4.3 Redis Integration Tests

**File**: `src/test/java/io/github/ashr123/warmestdata/RedisWarmestDataStructureTest.java`

Using Testcontainers with Redis.

Same 21 test cases but running against Redis implementation.

---

## IMPLEMENTATION CHECKLIST

### Part 1: Core Data Structure

1. [ ] Create file `src/main/java/io/github/ashr123/warmestdata/WarmestDataStructureInterface.java` with interface
   definition
2. [ ] Create file `src/main/java/io/github/ashr123/warmestdata/WarmestDataStructure.java` with private static inner
   class `Node`
3. [ ] Add fields: `map`, `head`, `tail`, `lock` to `WarmestDataStructure`
4. [ ] Implement helper method `moveToTail(Node node)`
5. [ ] Implement helper method `detach(Node node)`
6. [ ] Implement helper method `attachToTail(Node node)`
7. [ ] Implement `put(String key, int value)` method with write lock
8. [ ] Implement `get(String key)` method with write lock
9. [ ] Implement `remove(String key)` method with write lock
10. [ ] Implement `getWarmest()` method with read lock

### Part 2: REST API

11. [ ] Create directory `src/main/java/io/github/ashr123/warmestdata/config/`
12. [ ] Create file `WarmestDataConfig.java` with bean configuration
13. [ ] Create directory `src/main/java/io/github/ashr123/warmestdata/controller/`
14. [ ] Create file `WarmestDataController.java` with constructor injection
15. [ ] Implement `PUT /data/{key}` endpoint with raw integer body
16. [ ] Implement `GET /data/{key}` endpoint
17. [ ] Implement `DELETE /data/{key}` endpoint
18. [ ] Implement `GET /warmest` endpoint

### Part 3: Redis Implementation

19. [ ] Create directory `src/main/resources/scripts/`
20. [ ] Create file `put.lua` with Lua script
21. [ ] Create file `get.lua` with Lua script
22. [ ] Create file `remove.lua` with Lua script
23. [ ] Create file `getWarmest.lua` with Lua script
24. [ ] Create directory `src/main/java/io/github/ashr123/warmestdata/redis/`
25. [ ] Create file `RedisWarmestDataStructure.java` implementing `WarmestDataStructureInterface`
26. [ ] Create file `RedisConfig.java` with script beans
27. [ ] Modify `WarmestDataConfig.java` to add `@Profile("!redis")` to local bean
28. [ ] Update `application.properties` with Redis configuration
29. [ ] Modify `compose.yaml` to expose Redis port 6379
30. [ ] Create file `Dockerfile`
31. [ ] Create file `compose-multi.yaml` for 3-instance deployment

### Part 4: Testing

32. [ ] Create file `WarmestDataStructureTest.java` with test class setup
33. [ ] Implement test cases 1-10 (single key operations)
34. [ ] Implement test cases 11-21 (multi-key operations)
35. [ ] Create file `WarmestDataControllerTest.java` with `@SpringBootTest`
36. [ ] Implement controller endpoint tests
37. [ ] Create file `RedisWarmestDataStructureTest.java` with Testcontainers
38. [ ] Implement all 21 test cases for Redis implementation
39. [ ] Run all tests to verify implementation

### Final Verification

40. [ ] Build project: `./gradlew build`
41. [ ] Run local application: `./gradlew bootRun`
42. [ ] Test endpoints manually with curl
43. [ ] Build Docker image: `./gradlew bootJar && docker build -t warmest-data .`
44. [ ] Deploy 3 instances: `docker-compose -f compose-multi.yaml up`
45. [ ] Verify data synchronization across instances
