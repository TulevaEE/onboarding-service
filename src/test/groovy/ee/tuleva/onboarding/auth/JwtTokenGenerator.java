package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.*;
import static ee.tuleva.onboarding.auth.jwt.TokenType.ACCESS;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.auth.jwt.TokenType;
import ee.tuleva.onboarding.auth.principal.Person;
import io.jsonwebtoken.Jwts;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class JwtTokenGenerator {

  public static HttpHeaders getHeaders() {
    var jwtToken = generateDefaultJwtToken(samplePerson());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);

    return headers;
  }

  public static HttpHeaders getHeaders(Person person) {
    var jwtToken = generateDefaultJwtToken(person);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);

    return headers;
  }

  public static String generateDefaultJwtToken(Person person) {
    return generateJwtToken(person, ACCESS, List.of(USER));
  }

  public static String generateJwtToken(
      Person person, TokenType tokenType, List<String> authorities) {
    return generateToken(
        tokenType,
        authorities,
        "test-jwt-keystore.p12",
        "Kalamaja123",
        "jwt",
        "Kalamaja123",
        person);
  }

  public static String generateToken(
      TokenType tokenType,
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
          .subject(person.getPersonalCode())
          .claim(TOKEN_TYPE.getValue(), tokenType)
          .claim(FIRST_NAME.getValue(), person.getFirstName())
          .claim(LAST_NAME.getValue(), person.getLastName())
          .claim(ATTRIBUTES.getValue(), Map.of(PHONE_NUMBER, "+372 555 5555"))
          .claim(AUTHORITIES.getValue(), authorities)
          .issuedAt(new Date())
          .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour expiration
          .signWith(privateKey, Jwts.SIG.RS256)
          .compact();
    } catch (Exception e) {
      throw new RuntimeException();
    }
  }
}
