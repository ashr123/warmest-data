package io.github.ashr123.warmestdata.dto;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@Profile("redis")
public class RedisWarmestDataStructure implements WarmestDataStructureInterface {

	private static final String DATA_KEY = "warmest:data";
	private static final String PREV_KEY = "warmest:prev";
	private static final String NEXT_KEY = "warmest:next";
	private static final String TAIL_KEY = "warmest:tail";
	private static final List<String> WARMEST_KEYS = List.of(TAIL_KEY);
	private static final List<String> KEYS = Arrays.asList(DATA_KEY, PREV_KEY, NEXT_KEY, TAIL_KEY);

	private static final RedisScript<String> PUT_SCRIPT = RedisScript.of(new ClassPathResource("scripts/put.lua"), String.class);
	private static final RedisScript<String> GET_SCRIPT = RedisScript.of(new ClassPathResource("scripts/get.lua"), String.class);
	private static final RedisScript<String> REMOVE_SCRIPT = RedisScript.of(new ClassPathResource("scripts/remove.lua"), String.class);
	private static final RedisScript<String> GET_WARMEST_SCRIPT = RedisScript.of(new ClassPathResource("scripts/getWarmest.lua"), String.class);

	private final StringRedisTemplate redisTemplate;

	public RedisWarmestDataStructure(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public Integer put(String key, int value) {
		String result = redisTemplate.execute(PUT_SCRIPT, KEYS, key, String.valueOf(value));
		return result == null ? null : Integer.parseInt(result);
	}

	@Override
	public Integer get(String key) {
		String result = redisTemplate.execute(GET_SCRIPT, KEYS, key);
		return result == null ? null : Integer.parseInt(result);
	}

	@Override
	public Integer remove(String key) {
		String result = redisTemplate.execute(REMOVE_SCRIPT, KEYS, key);
		return result == null ? null : Integer.parseInt(result);
	}

	@Override
	public String getWarmest() {
		return redisTemplate.execute(GET_WARMEST_SCRIPT, WARMEST_KEYS);
	}
}
