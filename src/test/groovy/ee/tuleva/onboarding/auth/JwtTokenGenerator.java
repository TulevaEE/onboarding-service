package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.*;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.TOKEN_TYPE;

import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.jwt.TokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

public class JwtTokenGenerator {

  public static String generateDefaultJwtToken() {
    return JwtTokenGenerator.generateToken(
        List.of("ROLE_USER"), "test-jwt-keystore.p12", "Kalamaja123", "jwt", "Kalamaja123");
  }

  public static String generateToken(
      List<String> authorities,
      String keystorePath,
      String keystorePassword,
      String alias,
      String keyPassword) {
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");

      try (InputStream keyStoreStream =
          JwtTokenGenerator.class.getClassLoader().getResourceAsStream(keystorePath)) {
        if (keyStoreStream == null) {
          throw new RuntimeException("Keystore file not found: " + keystorePath);
        }
        keyStore.load(keyStoreStream, keystorePassword.toCharArray());
      }

      PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword.toCharArray());

      if (privateKey == null) {
        throw new RuntimeException("Private key is null for alias: " + alias);
      }

      var aPerson = samplePerson();

      return Jwts.builder()
          .setSubject(aPerson.getPersonalCode())
          .claim("authorities", authorities)
          .claim(TOKEN_TYPE.getValue(), TokenType.ACCESS)
          .claim(FIRST_NAME.getValue(), aPerson.getFirstName())
          .claim(LAST_NAME.getValue(), aPerson.getLastName())
          .claim(AUTHORITIES.getValue(), List.of(Authority.USER))
          .setIssuedAt(new Date())
          .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour expiration
          .signWith(privateKey, SignatureAlgorithm.RS256)
          .compact();
    } catch (Exception e) {
      throw new RuntimeException();
    }
  }
}
