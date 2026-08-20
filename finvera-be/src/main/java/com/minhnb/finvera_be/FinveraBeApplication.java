package com.minhnb.finvera_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinveraBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinveraBeApplication.class, args);
	}

}
