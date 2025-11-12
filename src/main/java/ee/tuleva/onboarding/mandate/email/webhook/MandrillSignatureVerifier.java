package ee.tuleva.onboarding.mandate.email.webhook;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import jakarta.servlet.http.HttpServletRequest;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @see <a
 *     href="https://mailchimp.com/developer/transactional/docs/webhooks/#authenticating-webhook-requests">Mandrill
 *     Webhook Authentication</a>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MandrillSignatureVerifier {

  @Value("${mandrill.webhook.key}")
  private String webhookKey;

  @Value("${api.url}")
  private String apiUrl;

  private static final String WEBHOOK_PATH = "/v1/emails/webhooks/mandrill";

  public boolean verify(HttpServletRequest request, String receivedSignature) {
    if (receivedSignature == null || receivedSignature.isEmpty()) {
      log.warn("Mandrill webhook received without signature");
      return false;
    }

    if (webhookKey == null || webhookKey.isEmpty()) {
      log.error("Mandrill webhook key is not configured - signature verification required");
      return false;
    }

    try {
      String webhookUrl = apiUrl + WEBHOOK_PATH;
      String signedData = buildSignedData(webhookUrl, request.getParameterMap());
      String expectedSignature = generateSignature(signedData);

      boolean isValid = constantTimeEquals(expectedSignature, receivedSignature);

      if (!isValid) {
        log.warn("Mandrill webhook signature verification failed");
      }

      return isValid;

    } catch (Exception e) {
      log.error("Error verifying Mandrill webhook signature", e);
      return false;
    }
  }

  private boolean constantTimeEquals(String expected, String received) {
    return MessageDigest.isEqual(expected.getBytes(UTF_8), received.getBytes(UTF_8));
  }

  private String buildSignedData(String url, Map<String, String[]> parameters) {
    String paramString =
        parameters.entrySet().stream()
            .sorted(comparingByKey())
            .map(
                entry -> {
                  String key = entry.getKey();
                  String value = entry.getValue().length > 0 ? entry.getValue()[0] : "";
                  return key + value;
                })
            .collect(joining());

    return url + paramString;
  }

  private String generateSignature(String data)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance("HmacSHA1");
    Key secretKey = new SecretKeySpec(webhookKey.getBytes(UTF_8), "HmacSHA1");
    mac.init(secretKey);

    byte[] hmac = mac.doFinal(data.getBytes(UTF_8));
    return Base64.getEncoder().encodeToString(hmac);
  }
}
