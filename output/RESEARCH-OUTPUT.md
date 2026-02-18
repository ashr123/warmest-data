[MODE: RESEARCH]

# Research Findings: HomeProjectForTalent.docx.html

## Document Overview

This is a **home project for a Java Developer candidate position** consisting of **3 parts**.

---

## PART 1: WarmestDataStructure Interface Implementation

### Requirements

- Implement `WarmestDataStructureInterface` with **O(1) complexity** for all operations.

### Interface Definition

```java
public interface WarmestDataStructureInterface {
	/**
	 * Associates the specified value with the specified key
	 * Complexity: O(1)
	 * @param key   The key with which the specified value is to be associated.
	 * @param value The value to be associated with the specified key.
	 * @return the previous value associated with key, or null if there was no mapping for key.
	 */
	public Integer put(String key, int value);

	/**
	 * Removes the mapping for the specified key if present.
	 * Complexity: O(1)
	 * @param key The key whose mapping is to be removed from the map.
	 * @return the previous value associated with key, or null if there was no mapping for key.
	 */
	public Integer remove(String key);

	/**
	 * Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
	 * Complexity: O(1)
	 * @param key The key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key.
	 */
	public Integer get(String key);

	/**
	 * Get the "warmest" key in the system
	 * Complexity: O(1)
	 * @return the last key that was passed in methods put or get, and was not removed
	 */
	public String getWarmest();
}
```

### Test Cases Provided

| #  | Operation     | Expected Result |
|----|---------------|-----------------|
| 1  | GetWarmest()  | null            |
| 2  | put("a", 100) | null            |
| 3  | GetWarmest()  | a               |
| 4  | put("a", 101) | 100             |
| 5  | put("a", 101) | 101             |
| 6  | Get("a")      | 101             |
| 7  | GetWarmest()  | a               |
| 8  | Remove("a")   | 101             |
| 9  | Remove("a")   | null            |
| 10 | GetWarmest()  | null            |
| 11 | put("a", 100) | null            |
| 12 | put("b", 200) | null            |
| 13 | put("c", 300) | null            |
| 14 | GetWarmest()  | c               |
| 15 | Remove("b")   | 200             |
| 16 | GetWarmest()  | c               |
| 17 | Remove("c")   | 300             |
| 18 | GetWarmest()  | a               |
| 19 | Remove("a")   | 100             |
| 20 | GetWarmest()  | null            |
| 21 | Remove("a")   | null            |

### Key Observations from Test Cases

- `getWarmest()` returns the **last key that was passed to `put` or `get`** and **was not removed**
- When the warmest key is removed, it falls back to the **previous warmest key**
- This implies a **stack-like or ordered structure** for tracking "warmth"
- Both `put` and `get` operations update the "warmest" key
- `remove` only removes, does not update warmest (unless the warmest key itself is removed)

---

## PART 2: REST API Exposure

### Requirements

- Make `WarmestDataStructure` accessible over the WWW
- Multiple clients (web browsers, mobile apps, external services) should access it **simultaneously**

### Hint Provided

- **Spring Boot with REST API**

---

## PART 3: Distributed Synchronization

### Requirements

- Application running on **3 different servers** (ports: 8080, 8081, 8082)
- Each instance has its own in-memory data (not synchronized)
- Need technology to **keep all instances sharing the same data structure**
- Must maintain **O(1) performance** and **insertion order**

### Hint Provided

- Use **Docker** to load the technology solution
- Integrate `WarmestDataStructure` application with this technology
- **No changes** to the requested interface
- **Maintain O(1) performance**

---

## Current Project State Analysis

### Project Structure

- **Framework**: Spring Boot 4.0.2 (Gradle build)
- **Java Version**: 17
- **Package**: `io.github.ashr123.warmestdata`

### Existing Dependencies (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

### Existing Docker Compose (compose.yaml)

```yaml
services:
  redis:
    image: 'redis:latest'
    ports:
      - '6379'
```

### Existing Source Files

1. **WarmestDataApplication.java** - Standard Spring Boot application entry point
2. **application.properties** - Only contains `spring.application.name=warmest-data`

### Existing Test Files

1. **TestcontainersConfiguration.java** - Sets up Redis testcontainer
2. **TestWarmestDataApplication.java** - Test application runner with testcontainers
3. **WarmestDataApplicationTests.java** - Basic context load test

---

## Questions for Clarification

1. **Interface Location**: Should the `WarmestDataStructureInterface` be created as a new Java file, or is there an
   expectation of a specific naming convention?

2. **REST API Design**:
    - Should the REST API follow RESTful conventions (e.g., `POST /data/{key}` for put, `DELETE /data/{key}` for
      remove)?
    - Or should it mirror the interface method names (e.g., `/put`, `/remove`, `/get`, `/getWarmest`)?

3. **Thread Safety**: For Part 2, should the local implementation be thread-safe for concurrent access, or will thread
   safety be handled at a different layer?

4. **Redis Data Structures**: For Part 3, should the implementation use:
    - Redis Hashes for key-value storage?
    - Redis Lists or Sorted Sets for maintaining insertion order?
    - Redis Lua scripts for atomic operations?

5. **Fallback Behavior**: When `getWarmest` returns the previous warmest key after removal, should this be:
    - Based on insertion order (first in, last out)?
    - Based on last access time?

6. **Test Coverage**: Should comprehensive unit tests be created for all test cases provided in the document?
