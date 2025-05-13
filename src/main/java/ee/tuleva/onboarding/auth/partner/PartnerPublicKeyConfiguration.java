package ee.tuleva.onboarding.auth.partner;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class PartnerPublicKeyConfiguration {

  @Bean
  @SneakyThrows
  public PublicKey partnerPublicKey1(@Value("${partner.publicKey1}") Resource partnerPublicKey1) {
    byte[] publicKey1Bytes = partnerPublicKey1.getInputStream().readAllBytes();
    return toPublicKey(publicKey1Bytes);
  }

  @Bean
  @SneakyThrows
  public PublicKey partnerPublicKey2(@Value("${partner.publicKey2}") Resource partnerPublicKey2) {
    byte[] publicKey2Bytes = partnerPublicKey2.getInputStream().readAllBytes();
    return toPublicKey(publicKey2Bytes);
  }

  @SneakyThrows
  private PublicKey toPublicKey(byte[] publicKeyBytes) {
    String key = new String(publicKeyBytes);
    String publicKeyPEM =
        key.replace("-----BEGIN PUBLIC KEY-----", "")
            .replaceAll("\r", "")
            .replaceAll("\n", "")
            .replace("-----END PUBLIC KEY-----", "");

    byte[] encodedKey = Base64.getDecoder().decode(publicKeyPEM);
    var keySpec = new X509EncodedKeySpec(encodedKey);
    var keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(keySpec);
  }
}
