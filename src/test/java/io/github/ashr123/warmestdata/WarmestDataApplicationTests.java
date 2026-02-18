package io.github.ashr123.warmestdata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WarmestDataApplicationTests {

	@Test
	void contextLoads() {
	}

}
