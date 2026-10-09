package ee.tuleva.onboarding.oauth.server;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.Resource;

final class RsaPem {

  private RsaPem() {}

  static RSAPublicKey publicKey(Resource pem) {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(der(pem, "PUBLIC KEY")));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unreadable RSA public key: resource=" + pem, e);
    }
  }

  static RSAPrivateCrtKey privateKey(Resource pem) {
    try {
      return (RSAPrivateCrtKey)
          KeyFactory.getInstance("RSA")
              .generatePrivate(new PKCS8EncodedKeySpec(der(pem, "PRIVATE KEY")));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unreadable RSA private key: resource=" + pem, e);
    }
  }

  static RSAPublicKey publicKeyOf(RSAPrivateCrtKey privateKey) {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA")
              .generatePublic(
                  new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot derive RSA public key", e);
    }
  }

  private static byte[] der(Resource pem, String type) {
    try (var in = pem.getInputStream()) {
      var base64 =
          new String(in.readAllBytes(), US_ASCII)
              .replace("-----BEGIN " + type + "-----", "")
              .replace("-----END " + type + "-----", "")
              .replaceAll("\\s", "");
      return Base64.getDecoder().decode(base64);
    } catch (IOException e) {
      throw new UncheckedIOException("Unreadable key file: resource=" + pem, e);
    }
  }
}
