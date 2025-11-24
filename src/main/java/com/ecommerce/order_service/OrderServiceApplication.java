package com.ecommerce.order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.ecommerce.order_service.api.mapper.OrderMapperImpl;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(OrderMapperImpl.class)
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
