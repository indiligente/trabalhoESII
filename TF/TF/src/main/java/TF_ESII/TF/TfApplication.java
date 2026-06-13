package TF_ESII.TF;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class TfApplication {

	public static void main(String[] args) {
		SpringApplication.run(TfApplication.class, args);
	}

}
