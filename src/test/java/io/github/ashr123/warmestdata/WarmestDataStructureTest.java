package io.github.ashr123.warmestdata;

import io.github.ashr123.warmestdata.dto.WarmestDataStructure;
import org.junit.jupiter.api.*;

/**
 * Unit tests for WarmestDataStructure implementing all 21 test cases from the specification.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WarmestDataStructureTest {

	private WarmestDataStructure dataStructure;

	@BeforeEach
	void setUp() {
		dataStructure = new WarmestDataStructure();
	}

	// ==================== Single Key Operations (Test Cases 1-10) ====================

	@Test
	@Order(1)
	void test01_getWarmest_whenEmpty_returnsNull() {
		// GetWarmest() → returns null
		Assertions.assertNull(dataStructure.getWarmest());
	}

	@Test
	@Order(2)
	void test02_put_whenNewKey_returnsNull() {
		// put("a", 100) → returns null
		Assertions.assertNull(dataStructure.put("a", 100));
	}

	@Test
	@Order(3)
	void test03_getWarmest_afterPut_returnsKey() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// GetWarmest() → returns a
		Assertions.assertEquals("a", dataStructure.getWarmest());
	}

	@Test
	@Order(4)
	void test04_put_whenKeyExists_returnsPreviousValue() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("a", 101) → returns 100
		Assertions.assertEquals(100, dataStructure.put("a", 101));
	}

	@Test
	@Order(5)
	void test05_put_whenKeyExistsWithSameValue_returnsPreviousValue() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("a", 101)
		dataStructure.put("a", 101);
		// put("a", 101) → returns 101
		Assertions.assertEquals(101, dataStructure.put("a", 101));
	}

	@Test
	@Order(6)
	void test06_get_returnsValue() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("a", 101)
		dataStructure.put("a", 101);
		// Get("a") → returns 101
		Assertions.assertEquals(101, dataStructure.get("a"));
	}

	@Test
	@Order(7)
	void test07_getWarmest_afterGet_returnsKey() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("a", 101)
		dataStructure.put("a", 101);
		// Get("a")
		dataStructure.get("a");
		// GetWarmest() → returns a
		Assertions.assertEquals("a", dataStructure.getWarmest());
	}

	@Test
	@Order(8)
	void test08_remove_returnsValue() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("a", 101)
		dataStructure.put("a", 101);
		// Remove("a") → return 101
		Assertions.assertEquals(101, dataStructure.remove("a"));
	}

	@Test
	@Order(9)
	void test09_remove_whenKeyNotExists_returnsNull() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// Remove("a")
		dataStructure.remove("a");
		// Remove("a") → return null
		Assertions.assertNull(dataStructure.remove("a"));
	}

	@Test
	@Order(10)
	void test10_getWarmest_afterRemovingOnlyKey_returnsNull() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// Remove("a")
		dataStructure.remove("a");
		// GetWarmest() → returns null
		Assertions.assertNull(dataStructure.getWarmest());
	}

	// ==================== Multi-Key Operations (Test Cases 11-21) ====================

	@Test
	@Order(11)
	void test11_put_multipleKeys_firstKeyReturnsNull() {
		// put("a", 100) → returns null
		Assertions.assertNull(dataStructure.put("a", 100));
	}

	@Test
	@Order(12)
	void test12_put_multipleKeys_secondKeyReturnsNull() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200) → returns null
		Assertions.assertNull(dataStructure.put("b", 200));
	}

	@Test
	@Order(13)
	void test13_put_multipleKeys_thirdKeyReturnsNull() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200)
		dataStructure.put("b", 200);
		// put("c", 300) → returns null
		Assertions.assertNull(dataStructure.put("c", 300));
	}

	@Test
	@Order(14)
	void test14_getWarmest_afterMultiplePuts_returnsLastKey() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200)
		dataStructure.put("b", 200);
		// put("c", 300)
		dataStructure.put("c", 300);
		// GetWarmest() → returns c
		Assertions.assertEquals("c", dataStructure.getWarmest());
	}

	@Test
	@Order(15)
	void test15_remove_middleKey_returnsValue() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200)
		dataStructure.put("b", 200);
		// put("c", 300)
		dataStructure.put("c", 300);
		// Remove("b") → return 200
		Assertions.assertEquals(200, dataStructure.remove("b"));
	}

	@Test
	@Order(16)
	void test16_getWarmest_afterRemovingMiddleKey_returnsLastKey() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200)
		dataStructure.put("b", 200);
		// put("c", 300)
		dataStructure.put("c", 300);
		// Remove("b")
		dataStructure.remove("b");
		// GetWarmest() → returns c
		Assertions.assertEquals("c", dataStructure.getWarmest());
	}

	@Test
	@Order(17)
	void test17_remove_lastKey_returnsValue() {
		// put("a", 100)
		dataStructure.put("a", 100);
		// put("b", 200)
		dataStructure.put("b", 200);
		// put("c", 300)
		dataStructure.put("c", 300);
		// Remove("b")
		dataStructure.remove("b");
		// Remove("c") → return 300
		Assertions.assertEquals(300, dataStructure.remove("c"));
	}

	@Test
	@Order(18)
	void test18_getWarmest_afterRemovingWarmestKey_returnsPreviousWarmest() {
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
		// GetWarmest() → returns a
		Assertions.assertEquals("a", dataStructure.getWarmest());
	}

	@Test
	@Order(19)
	void test19_remove_remainingKey_returnsValue() {
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
		// Remove("a") → return 100
		Assertions.assertEquals(100, dataStructure.remove("a"));
	}

	@Test
	@Order(20)
	void test20_getWarmest_afterRemovingAllKeys_returnsNull() {
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
		// GetWarmest() → returns null
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
