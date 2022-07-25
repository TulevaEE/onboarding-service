package ee.tuleva.onboarding;

import java.security.Security;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class OnboardingServiceApplication {

  public static void main(String[] args) {
    Security.addProvider(new BouncyCastleProvider());
    SpringApplication.run(OnboardingServiceApplication.class, args);
    validateFileEncoding();
  }

  private static void validateFileEncoding() {
    if (!System.getProperty("file.encoding", "").equalsIgnoreCase("utf-8")) {
      log.error("Unsupported file encoding {}!", System.getProperty("file.encoding"));
    }
  }
}
