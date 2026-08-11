package az.turn.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TurnApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnApiApplication.class, args);
    }
}
