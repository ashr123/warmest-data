package io.github.ashr123.warmestdata;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs all race condition scenarios against the in-memory
 * {@link io.github.ashr123.warmestdata.dto.WarmestDataStructure} implementation.
 * No active profile → {@code redis} is inactive → {@code @Profile("!redis")} selects
 * {@link io.github.ashr123.warmestdata.dto.WarmestDataStructure}.
 */
@SpringBootTest
class WarmestDataStructureRaceConditionTest extends AbstractRaceConditionTest {
	// All 10 test scenarios are inherited from AbstractRaceConditionTest.
	// In-memory is fast, so the default 1 000 iterations are used.
}
