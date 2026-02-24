package io.github.ashr123.warmestdata;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs all race condition scenarios against the Redis-backed
 * {@link io.github.ashr123.warmestdata.dto.RedisWarmestDataStructure} implementation
 * (Spring profile {@code redis}) using a Testcontainers Redis instance.
 */
@SpringBootTest
@ActiveProfiles("redis")
@Import(TestcontainersConfiguration.class)
class RedisWarmestDataStructureRaceConditionTest extends AbstractRaceConditionTest {
	// All 10 test scenarios are inherited from AbstractRaceConditionTest.
	// Both profiles use 1 000 iterations.
}
