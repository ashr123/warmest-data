package io.github.ashr123.warmestdata;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs all 21 functional scenarios against the in-memory
 * {@link io.github.ashr123.warmestdata.dto.WarmestDataStructure} implementation.
 * No active profile → {@code redis} is inactive → {@code @Profile("!redis")} selects
 * {@link io.github.ashr123.warmestdata.dto.WarmestDataStructure}.
 */
@SpringBootTest
class WarmestDataStructureTest extends AbstractWarmestDataStructureTest {
	// All 21 test cases are inherited from AbstractWarmestDataStructureTest.
}
