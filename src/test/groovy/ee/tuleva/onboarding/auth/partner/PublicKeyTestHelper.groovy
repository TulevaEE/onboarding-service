package ee.tuleva.onboarding.auth.partner

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource

import java.security.PublicKey
import java.security.interfaces.RSAPublicKey

class PublicKeyTestHelper {

  static Resource publicKeyToResource(PublicKey publicKey) {
    String pem = publicKeyToPemFormat(publicKey)
    byte[] pemBytes = pem.getBytes()
    return new ByteArrayResource(pemBytes)
  }

  private static String publicKeyToPemFormat(PublicKey publicKey) {
    RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey
    byte[] encodedKey = rsaPublicKey.getEncoded()
    String base64Encoded = Base64.getEncoder().encodeToString(encodedKey)
    return "-----BEGIN PUBLIC KEY-----\r\n" + base64Encoded + "\r\n-----END PUBLIC KEY-----\r\n"
  }
}
