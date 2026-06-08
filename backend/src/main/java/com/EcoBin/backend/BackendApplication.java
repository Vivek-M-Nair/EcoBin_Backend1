package com.EcoBin.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		// Force the cloud system property directly into the runtime environment
		System.setProperty("spring.mongodb.uri",
				"mongodb+srv://viveksocs_db_user:CnXVN9Sv4S3hex7I@cluster0.ydjqaza.mongodb.net/EcoBinDB?retryWrites=true&w=majority");

		SpringApplication.run(BackendApplication.class, args);
	}
}
