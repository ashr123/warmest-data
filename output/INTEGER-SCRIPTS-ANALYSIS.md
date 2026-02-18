# Alternative Approach: Using Integer Scripts (NOT RECOMMENDED)

If you really want to use `Integer.class` for the value-returning scripts, here's how:

## Option 1: Split Script Types (Partial Solution)

```java

@Configuration
@Profile("redis")
public class RedisConfig {

	// These return integers (values)
	@Bean
	public RedisScript<Long> putScript() {
		return RedisScript.of(new ClassPathResource("scripts/put.lua"), Long.class);
	}

	@Bean
	public RedisScript<Long> getScript() {
		return RedisScript.of(new ClassPathResource("scripts/get.lua"), Long.class);
	}

	@Bean
	public RedisScript<Long> removeScript() {
		return RedisScript.of(new ClassPathResource("scripts/remove.lua"), Long.class);
	}

	// This returns a key (string)
	@Bean
	public RedisScript<String> getWarmestScript() {
		return RedisScript.of(new ClassPathResource("scripts/getWarmest.lua"), String.class);
	}
}
```

**Note**: Use `Long.class` instead of `Integer.class` because Redis returns numbers as Long.

Then update `RedisWarmestDataStructure`:

```java
private final RedisScript<Long> putScript;
private final RedisScript<Long> getScript;
private final RedisScript<Long> removeScript;
private final RedisScript<String> getWarmestScript;

@Override
public Integer put(String key, int value) {
	Long result = redisTemplate.execute(putScript, KEYS, key, String.valueOf(value));
	return result == null ? null : result.intValue();
}
```

## Why This Is NOT Better:

1. **Still Need Conversion**: `Long` → `int` conversion required
2. **Type Mismatch**: Redis returns `Long`, but we need `Integer`
3. **More Complex**: Mixed script types harder to maintain
4. **No Real Benefit**: Just shifting conversion from `parseInt()` to `intValue()`

## Conclusion:

**KEEP THE CURRENT `String.class` IMPLEMENTATION** ✅

It's:

- ✅ Simpler
- ✅ More explicit
- ✅ Easier to debug
- ✅ Consistent across all scripts
- ✅ Standard Spring Data Redis pattern

The `Integer.parseInt()` overhead is **negligible** compared to network I/O.
