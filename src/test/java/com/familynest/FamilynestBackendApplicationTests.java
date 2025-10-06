package com.familynest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.familynest.config.TestConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class FamilynestBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

