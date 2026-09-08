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

//		System.out.println("customer_account subintents" + toolIndexService.getFileToolIndex().get("customer_account"));
//		System.out.println("customer_account enterprise_b2b" + toolIndexService.getFileToolIndex().get("enterprise_b2b"));
		System.out.println("orders subintents" + toolIndexService.getFileToolIndex().get("orders"));
//		System.out.println("products subintents" + toolIndexService.getFileToolIndex().get("products"));
//		System.out.println("shopping_experience subintents" + toolIndexService.getFileToolIndex().get("shopping_experience"));
//		System.out.println("store_general subintents" + toolIndexService.getFileToolIndex().get("store_general"));
//		System.out.println("support_escalation subintents" + toolIndexService.getFileToolIndex().get("support_escalation"));
//		System.out.println("payments_promotions subintents" + toolIndexService.getFileToolIndex().get("payments_promotions"));
	}
}
