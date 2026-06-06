package com.EcoBin.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// Force the cloud system property directly into the runtime environment
		System.setProperty("spring.data.mongodb.uri",
				"mongodb+srv://viveksocs_db_user:CnXVN9Sv4S3hex7I@cluster0.ydjqaza.mongodb.net/?appName=Cluster0");

		SpringApplication.run(BackendApplication.class, args);
	}
}
