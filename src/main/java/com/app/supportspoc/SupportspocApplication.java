package com.app.supportspoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class SupportspocApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupportspocApplication.class, args);
	}

}
