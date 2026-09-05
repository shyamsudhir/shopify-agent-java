package com.app.supportspoc;

import com.app.supportspoc.util.ToolIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class SupportspocApplication implements CommandLineRunner {

	@Autowired
	private ToolIndexService toolIndexService;

	public static void main(String[] args) {
		SpringApplication.run(SupportspocApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

	}
}
