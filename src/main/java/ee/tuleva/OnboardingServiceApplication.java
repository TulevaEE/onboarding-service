package ee.tuleva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class OnboardingServiceApplication {

    public static void main(String[] args) {
        //disabling standard security is necessary to connect to EPIS
        Security.setProperty("jdk.tls.disabledAlgorithms", "RC4, MD5withRSA, DH keySize < 768, EC keySize < 224");
        SpringApplication.run(OnboardingServiceApplication.class, args);
    }

}