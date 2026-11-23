package ee.tuleva.onboarding.investment.transaction.export;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import net.minidev.json.JSONObject;

final class GoogleServiceAccountFixture {

  private GoogleServiceAccountFixture() {}

  static KeyPair generatedKeyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static String serviceAccountJson() {
    return serviceAccountJson(generatedKeyPair());
  }

  static String serviceAccountJson(KeyPair keyPair) {
    var json =
        new JSONObject(
            Map.of(
                "type", "service_account",
                "client_email", "test@project.iam.gserviceaccount.com",
                "private_key", pemEncodedPrivateKey(keyPair),
                "token_uri", "https://oauth2.googleapis.com/token"));
    return Base64.getEncoder().encodeToString(json.toJSONString().getBytes(UTF_8));
  }

  private static String pemEncodedPrivateKey(KeyPair keyPair) {
    var encoded = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
  }
}
