package ee.tuleva.onboarding.oauth.server;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.nimbusds.jose.jwk.Curve;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.springframework.core.io.Resource;

final class EcPem {

  static final Curve CURVE = Curve.P_384;

  private EcPem() {}

  static ECPublicKey publicKey(Resource pem) {
    try {
      return onTheCurve(
          (ECPublicKey) keyFactory().generatePublic(new X509EncodedKeySpec(der(pem, "PUBLIC KEY"))),
          pem);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unreadable EC public key: resource=" + pem, e);
    }
  }

  static ECPrivateKey privateKey(Resource pem) {
    try {
      return onTheCurve(
          (ECPrivateKey)
              keyFactory().generatePrivate(new PKCS8EncodedKeySpec(der(pem, "PRIVATE KEY"))),
          pem);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unreadable EC private key: resource=" + pem, e);
    }
  }

  static ECPublicKey publicKeyOf(ECPrivateKey privateKey) {
    var point =
        ECNamedCurveTable.getParameterSpec(CURVE.getStdName())
            .getG()
            .multiply(privateKey.getS())
            .normalize();
    try {
      return (ECPublicKey)
          keyFactory()
              .generatePublic(
                  new ECPublicKeySpec(
                      new ECPoint(
                          point.getAffineXCoord().toBigInteger(),
                          point.getAffineYCoord().toBigInteger()),
                      privateKey.getParams()));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot derive EC public key", e);
    }
  }

  private static <K extends ECKey> K onTheCurve(K key, Resource pem) {
    if (!CURVE.equals(Curve.forECParameterSpec(key.getParams()))) {
      throw new IllegalStateException("EC key must be on " + CURVE.getName() + ": resource=" + pem);
    }
    return key;
  }

  private static KeyFactory keyFactory() throws GeneralSecurityException {
    return KeyFactory.getInstance("EC");
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
