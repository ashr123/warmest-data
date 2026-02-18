package io.github.ashr123.warmestdata.config;

import io.github.ashr123.warmestdata.dto.WarmestDataStructure;
import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class WarmestDataConfig {
	@Bean
	@Profile("!redis")
	public WarmestDataStructureInterface warmestDataStructure() {
		return new WarmestDataStructure();
	}
}
