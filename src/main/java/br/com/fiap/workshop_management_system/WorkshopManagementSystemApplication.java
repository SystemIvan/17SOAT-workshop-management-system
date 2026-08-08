package br.com.fiap.workshop_management_system;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkshopManagementSystemApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(WorkshopManagementSystemApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Hello word");
	}
}
