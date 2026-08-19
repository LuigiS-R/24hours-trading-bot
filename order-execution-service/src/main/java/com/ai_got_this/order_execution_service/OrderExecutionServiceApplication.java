package com.ai_got_this.order_execution_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderExecutionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderExecutionServiceApplication.class, args);
	}

}
