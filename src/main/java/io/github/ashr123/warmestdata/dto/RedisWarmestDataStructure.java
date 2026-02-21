package io.github.ashr123.warmestdata.dto;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("redis")
public class RedisWarmestDataStructure implements WarmestDataStructureInterface {

	private static final String DATA_KEY = "warmest:data";
	private static final String PREV_KEY = "warmest:prev";
	private static final String NEXT_KEY = "warmest:next";
	private static final String HEAD_KEY = "warmest:head";
	private static final String TAIL_KEY = "warmest:tail";
	private static final List<String> WARMEST_KEYS = List.of(TAIL_KEY);
	private static final List<String> KEYS = Arrays.asList(DATA_KEY, PREV_KEY, NEXT_KEY, HEAD_KEY, TAIL_KEY);

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<String> putScript;
	private final RedisScript<String> getScript;
	private final RedisScript<String> removeScript;
	private final RedisScript<String> getWarmestScript;

	public RedisWarmestDataStructure(
			StringRedisTemplate redisTemplate,
			RedisScript<String> putScript,
			RedisScript<String> getScript,
			RedisScript<String> removeScript,
			RedisScript<String> getWarmestScript) {
		this.redisTemplate = redisTemplate;
		this.putScript = putScript;
		this.getScript = getScript;
		this.removeScript = removeScript;
		this.getWarmestScript = getWarmestScript;
	}

	@Override
	public Integer put(String key, int value) {
		String result = redisTemplate.execute(putScript, KEYS, key, String.valueOf(value));
		return result == null ? null : Integer.parseInt(result);
	}

	@Override
	public Integer get(String key) {
		String result = redisTemplate.execute(getScript, KEYS, key);
		return result == null ? null : Integer.parseInt(result);
	}

	@Override
	public Integer remove(String key) {
		String result = redisTemplate.execute(removeScript, KEYS, key);
		return result == null ? null : Integer.parseInt(result);
	}

	@Override
	public String getWarmest() {
		return redisTemplate.execute(getWarmestScript, WARMEST_KEYS);
	}
}
