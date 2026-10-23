package com.curo.curo_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

@SpringBootTest
class CuroBackendApplicationTests {

	// Throwaway values generated per run, so no secret or real database is needed or committed
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
		registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
		registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
		registry.add("licence.verification.seed-on-startup", () -> "false");
	}

	@Test
	void contextLoads() {
	}

}
