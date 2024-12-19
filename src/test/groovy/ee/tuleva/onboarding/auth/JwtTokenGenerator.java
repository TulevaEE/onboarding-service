package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.*;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.TOKEN_TYPE;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.jwt.TokenType;
import ee.tuleva.onboarding.auth.principal.Person;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class JwtTokenGenerator {

  public static HttpHeaders getHeaders() {
    var jwtToken = JwtTokenGenerator.generateDefaultJwtToken(samplePerson());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);

    return headers;
  }

  public static HttpHeaders getHeaders(Person person) {
    var jwtToken = JwtTokenGenerator.generateDefaultJwtToken(person);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);

    return headers;
  }

  public static String generateDefaultJwtToken(Person person) {
    return JwtTokenGenerator.generateToken(
        List.of("ROLE_USER"), "test-jwt-keystore.p12", "Kalamaja123", "jwt", "Kalamaja123", person);
  }

  public static String generateToken(
      List<String> authorities,
      String keystorePath,
      String keystorePassword,
      String alias,
      String keyPassword,
      Person person) {
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

      return Jwts.builder()
          .setSubject(person.getPersonalCode())
          .claim("authorities", authorities)
          .claim(TOKEN_TYPE.getValue(), TokenType.ACCESS)
          .claim(FIRST_NAME.getValue(), person.getFirstName())
          .claim(LAST_NAME.getValue(), person.getLastName())
          .claim(ATTRIBUTES.getValue(), Map.of(PHONE_NUMBER, "+372 555 5555"))
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
