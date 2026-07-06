package TF_ESII.TF;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableRabbit
@EnableFeignClients
@SpringBootApplication
public class TfApplication {

	public static void main(String[] args) {
		SpringApplication.run(TfApplication.class, args);
	}

}
