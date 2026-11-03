package com.popq.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PopqPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(PopqPlatformApplication.class, args);
	}

}
