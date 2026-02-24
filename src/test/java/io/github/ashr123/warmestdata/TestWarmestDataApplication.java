package io.github.ashr123.warmestdata;

import org.springframework.boot.SpringApplication;

public class TestWarmestDataApplication {

	public static void main(String... args) {
		SpringApplication.from(WarmestDataApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
