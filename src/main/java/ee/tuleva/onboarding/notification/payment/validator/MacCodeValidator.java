package ee.tuleva.onboarding.notification.payment.validator;

import ee.tuleva.onboarding.notification.payment.IncomingPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
@Slf4j
public class MacCodeValidator implements ConstraintValidator<ValidMacCode, IncomingPayment> {

  @Value("${maksekeskus.secret}")
  private String secret;

  @Override
  public boolean isValid(IncomingPayment incomingPayment, ConstraintValidatorContext context) {
    String json = incomingPayment.getJson();
    String mac = incomingPayment.getMac();
    String payload = json + secret;
    String calculatedHash = sha512(payload);

    return calculatedHash.equalsIgnoreCase(mac);
  }

  @Override
  public void initialize(ValidMacCode constraintAnnotation) {
  }

  private static String sha512(String payload) {
    MessageDigest messageDigest = sha512MessageDigest();
    messageDigest.update(payload.getBytes(UTF_8));
    byte[] hash = messageDigest.digest();

    StringBuilder hex = new StringBuilder();
    for (byte b : hash) {
      hex.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
    }
    return hex.toString();
  }

  private static MessageDigest sha512MessageDigest() {
    try {
      return MessageDigest.getInstance("SHA-512");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
