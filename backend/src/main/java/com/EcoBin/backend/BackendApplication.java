package com.EcoBin.backend;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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

	@Bean
	public CommandLineRunner seedDatabase(AdminRepository adminRepository, OfficeStaffRepository officeStaffRepository) {
		return args -> {
			System.out.println("[DATABASE SEEDER] Checking seed data...");

			// Seed Admin
			if (!adminRepository.existsById("admin1")) {
				Admin admin = new Admin("admin1", "System Administrator", "adminpass");
				adminRepository.save(admin);
				System.out.println("[DATABASE SEEDER] Seeded default admin account: admin1 / adminpass");
			} else {
				System.out.println("[DATABASE SEEDER] Admin account admin1 already exists.");
			}

			// Seed Office Staff
			if (!officeStaffRepository.existsById("CS-1234")) {
				OfficeStaff staff = new OfficeStaff("CS-1234", "John Doe", "Operations Manager", "john.doe@ecobin.com", "+91 9900223344", "staffpass");
				officeStaffRepository.save(staff);
				System.out.println("[DATABASE SEEDER] Seeded default office staff account: CS-1234 / staffpass");
			} else {
				System.out.println("[DATABASE SEEDER] Office staff CS-1234 already exists.");
			}
		};
	}
}
