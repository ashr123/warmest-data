package io.github.ashr123.warmestdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
@Profile("redis")
public class RedisConfig {

	@Bean
	public RedisScript<String> putScript() {
		return RedisScript.of(new ClassPathResource("scripts/put.lua"), String.class);
	}

	@Bean
	public RedisScript<String> getScript() {
		return RedisScript.of(new ClassPathResource("scripts/get.lua"), String.class);
	}

	@Bean
	public RedisScript<String> removeScript() {
		return RedisScript.of(new ClassPathResource("scripts/remove.lua"), String.class);
	}

	@Bean
	public RedisScript<String> getWarmestScript() {
		return RedisScript.of(new ClassPathResource("scripts/getWarmest.lua"), String.class);
	}
}
