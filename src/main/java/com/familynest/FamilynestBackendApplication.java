package com.familynest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FamilynestBackendApplication {

	private static final Logger logger = LoggerFactory.getLogger(FamilynestBackendApplication.class);

	public static void main(String[] args) {
		logger.info("Starting FamilyNest Backend Application");
		SpringApplication.run(FamilynestBackendApplication.class, args);
	}
}

