package TF_ESII.TF;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class TfApplication {

	public static void main(String[] args) {
		SpringApplication.run(TfApplication.class, args);
	}

}
