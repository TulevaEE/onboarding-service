package ee.tuleva.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OnboardingServiceApplication {

    public static void main(String[] args) {
        // CloudFlare is not a fan of Java user agents
        System.setProperty("http.agent", "HTTPie/1.0.2");
        SpringApplication.run(OnboardingServiceApplication.class, args);
    }

}