package cn.scut.raputa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RaputaApplication {

	public static void main(String[] args) {
		SpringApplication.run(RaputaApplication.class, args);
	}

}
