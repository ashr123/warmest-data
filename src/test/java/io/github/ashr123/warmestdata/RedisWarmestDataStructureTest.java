package io.github.ashr123.warmestdata;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs all 21 functional scenarios against the Redis-backed
 * {@link io.github.ashr123.warmestdata.dto.RedisWarmestDataStructure} implementation
 * (Spring profile {@code redis}) using a Testcontainers Redis instance.
 */
@SpringBootTest
@ActiveProfiles("redis")
@Import(TestcontainersConfiguration.class)
class RedisWarmestDataStructureTest extends AbstractWarmestDataStructureTest {
	// All 21 test cases are inherited from AbstractWarmestDataStructureTest.
}
