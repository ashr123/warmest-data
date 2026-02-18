package io.github.ashr123.warmestdata;

import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for RedisWarmestDataStructure using Testcontainers.
 * Tests the same 21 scenarios as WarmestDataStructureTest but against Redis implementation.
 */
@SpringBootTest
@ActiveProfiles("redis")
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisWarmestDataStructureTest {

	@Autowired
	private WarmestDataStructureInterface dataStructure;

	@BeforeEach
	void setUp() {
		// Clear Redis data before each test
		while (dataStructure.getWarmest() != null) {
			dataStructure.remove(dataStructure.getWarmest());
		}
	}

	// ==================== Single Key Operations ====================

	@Test
	@Order(1)
	void test01_getWarmest_whenEmpty_returnsNull() {
		Assertions.assertNull(dataStructure.getWarmest());
	}

	@Test
	@Order(2)
	void test02_put_whenNewKey_returnsNull() {
		Assertions.assertNull(dataStructure.put("a", 100));
	}

	@Test
	@Order(3)
	void test03_getWarmest_afterPut_returnsKey() {
		dataStructure.put("a", 100);
		Assertions.assertEquals("a", dataStructure.getWarmest());
	}

	@Test
	@Order(4)
	void test04_put_whenKeyExists_returnsPreviousValue() {
		dataStructure.put("a", 100);
		Assertions.assertEquals(100, dataStructure.put("a", 101));
	}

	@Test
	@Order(5)
	void test05_put_whenKeyExistsWithSameValue_returnsPreviousValue() {
		dataStructure.put("a", 100);
		Assertions.assertEquals(100, dataStructure.put("a", 100));
	}

	@Test
	@Order(6)
	void test06_get_returnsValue() {
		dataStructure.put("a", 100);
		Assertions.assertEquals(100, dataStructure.get("a"));
	}

	@Test
	@Order(7)
	void test07_getWarmest_afterGet_returnsKey() {
		dataStructure.put("a", 100);
		dataStructure.get("a");
		Assertions.assertEquals("a", dataStructure.getWarmest());
	}

	@Test
	@Order(8)
	void test08_remove_returnsValue() {
		dataStructure.put("a", 100);
		Assertions.assertEquals(100, dataStructure.remove("a"));
	}

	@Test
	@Order(9)
	void test09_remove_whenKeyNotExists_returnsNull() {
		Assertions.assertNull(dataStructure.remove("nonexistent"));
	}

	@Test
	@Order(10)
	void test10_getWarmest_afterRemovingOnlyKey_returnsNull() {
		dataStructure.put("a", 100);
		dataStructure.remove("a");
		Assertions.assertNull(dataStructure.getWarmest());
	}

	// ==================== Multi-Key Operations ====================

	@Test
	@Order(11)
	void test11_put_multipleKeys_firstKeyReturnsNull() {
		Assertions.assertNull(dataStructure.put("a", 100));
	}

	@Test
	@Order(12)
	void test12_put_multipleKeys_secondKeyReturnsNull() {
		dataStructure.put("a", 100);
		Assertions.assertNull(dataStructure.put("b", 200));
	}

	@Test
	@Order(13)
	void test13_put_multipleKeys_thirdKeyReturnsNull() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		Assertions.assertNull(dataStructure.put("c", 300));
	}

	@Test
	@Order(14)
	void test14_getWarmest_afterMultiplePuts_returnsLastKey() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		Assertions.assertEquals("c", dataStructure.getWarmest());
	}

	@Test
	@Order(15)
	void test15_remove_middleKey_returnsValue() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		Assertions.assertEquals(200, dataStructure.remove("b"));
	}

	@Test
	@Order(16)
	void test16_getWarmest_afterRemovingMiddleKey_returnsLastKey() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		dataStructure.remove("b");
		Assertions.assertEquals("c", dataStructure.getWarmest());
	}

	@Test
	@Order(17)
	void test17_remove_lastKey_returnsValue() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		dataStructure.remove("b");
		Assertions.assertEquals(300, dataStructure.remove("c"));
	}

	@Test
	@Order(18)
	void test18_getWarmest_afterRemovingWarmestKey_returnsPreviousWarmest() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		dataStructure.remove("b");
		dataStructure.remove("c");
		Assertions.assertEquals("a", dataStructure.getWarmest());
	}

	@Test
	@Order(19)
	void test19_remove_remainingKey_returnsValue() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		dataStructure.remove("b");
		dataStructure.remove("c");
		Assertions.assertEquals(100, dataStructure.remove("a"));
	}

	@Test
	@Order(20)
	void test20_getWarmest_afterRemovingAllKeys_returnsNull() {
		dataStructure.put("a", 100);
		dataStructure.put("b", 200);
		dataStructure.put("c", 300);
		dataStructure.remove("b");
		dataStructure.remove("c");
		dataStructure.remove("a");
		Assertions.assertNull(dataStructure.getWarmest());
	}

	@Test
	@Order(21)
	void test21_remove_alreadyRemovedKey_returnsNull() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200)
		dataStructure.put("b", 200);
		// put("c", 300)
		dataStructure.put("c", 300);
		// Remove("b")
		dataStructure.remove("b");
		// Remove("c")
		dataStructure.remove("c");
		// Remove("a")
		dataStructure.remove("a");
		// Remove("a") → return null
		Assertions.assertNull(dataStructure.remove("a"));
	}
}
