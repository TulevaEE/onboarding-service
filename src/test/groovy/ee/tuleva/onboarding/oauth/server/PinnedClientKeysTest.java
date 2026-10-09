package ee.tuleva.onboarding.oauth.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class PinnedClientKeysTest {

  private static final String ISSUER = "http://localhost:9000";
  private static final String CLIENT_ID = "portfellow";

  private final PinnedClientKeys pinnedClientKeys =
      new PinnedClientKeys(
          new OAuthProperties(
              null,
              Map.of(
                  CLIENT_ID,
                  new OAuthProperties.Client(
                      "Portfellow",
                      List.of("https://app.example/callback"),
                      List.of("balances:read"),
                      List.of(
                          new ClassPathResource("oauth/test-client-public-key.pem"),
                          new ClassPathResource("oauth/test-client-rotated-public-key.pem"))))),
          Clock.systemUTC());

  private final RegisteredClient client =
      RegisteredClient.withId(CLIENT_ID)
          .clientId(CLIENT_ID)
          .authorizationGrantType(AUTHORIZATION_CODE)
          .redirectUri("https://app.example/callback")
          .build();

  @BeforeEach
  void setAuthorizationServerContext() {
    var settings = AuthorizationServerSettings.builder().issuer(ISSUER).build();
    AuthorizationServerContextHolder.setContext(
        new AuthorizationServerContext() {
          @Override
          public String getIssuer() {
            return ISSUER;
          }

          @Override
          public AuthorizationServerSettings getAuthorizationServerSettings() {
            return settings;
          }
        });
  }

  @AfterEach
  void resetAuthorizationServerContext() {
    AuthorizationServerContextHolder.resetContext();
  }

  @Test
  void anAssertionSignedWithTheFirstPinnedKeyIsAccepted() throws Exception {
    var jwt =
        pinnedClientKeys
            .createDecoder(client)
            .decode(assertion("oauth/test-client-private-key.pem", ISSUER, 60));

    assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
  }

  @Test
  void anAssertionSignedWithTheSecondPinnedKeyIsAccepted() throws Exception {
    var jwt =
        pinnedClientKeys
            .createDecoder(client)
            .decode(assertion("oauth/test-client-rotated-private-key.pem", ISSUER, 60));

    assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
  }

  @Test
  void anAssertionNamingItsKeyIdIsAcceptedThoughPinnedKeysHaveNone() throws Exception {
    var jwt =
        pinnedClientKeys
            .createDecoder(client)
            .decode(
                assertion(
                    "oauth/test-client-private-key.pem",
                    ISSUER,
                    60,
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("portfellow-2026-10").build()));

    assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
  }

  @Test
  void anAssertionSignedWithAnyOtherKeyIsRefused() throws Exception {
    var assertion = assertion("oauth/other-client-private-key.pem", ISSUER, 60);

    assertThatThrownBy(() -> pinnedClientKeys.createDecoder(client).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void anAssertionLivingLongerThanSixtySecondsIsRefused() throws Exception {
    var assertion = assertion("oauth/test-client-private-key.pem", ISSUER, 61);

    assertThatThrownBy(() -> pinnedClientKeys.createDecoder(client).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void anAssertionDatedInTheFutureIsRefusedThoughItLivesOnlySixtySeconds() throws Exception {
    var assertion =
        assertion(
            "oauth/test-client-private-key.pem",
            ISSUER,
            60,
            new JWSHeader(JWSAlgorithm.RS256),
            Instant.now().plus(30, ChronoUnit.DAYS));

    assertThatThrownBy(() -> pinnedClientKeys.createDecoder(client).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void anAssertionMeantForAnotherServerIsRefused() throws Exception {
    var assertion = assertion("oauth/test-client-private-key.pem", "https://elsewhere.example", 60);

    assertThatThrownBy(() -> pinnedClientKeys.createDecoder(client).decode(assertion))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void aClientWithoutPinnedKeysHasNoDecoder() {
    var unknown =
        RegisteredClient.withId("unknown")
            .clientId("unknown")
            .authorizationGrantType(AUTHORIZATION_CODE)
            .redirectUri("https://unknown.example/callback")
            .build();

    assertThatThrownBy(() -> pinnedClientKeys.createDecoder(unknown))
        .isInstanceOf(IllegalStateException.class);
  }

  private static String assertion(String privateKeyResource, String audience, int lifetimeSeconds)
      throws Exception {
    return assertion(
        privateKeyResource, audience, lifetimeSeconds, new JWSHeader(JWSAlgorithm.RS256));
  }

  private static String assertion(
      String privateKeyResource, String audience, int lifetimeSeconds, JWSHeader header)
      throws Exception {
    return assertion(privateKeyResource, audience, lifetimeSeconds, header, Instant.now());
  }

  private static String assertion(
      String privateKeyResource,
      String audience,
      int lifetimeSeconds,
      JWSHeader header,
      Instant now)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(CLIENT_ID)
            .subject(CLIENT_ID)
            .audience(audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(lifetimeSeconds)))
            .jwtID(UUID.randomUUID().toString())
            .build();
    var jwt = new SignedJWT(header, claims);
    jwt.sign(new RSASSASigner(privateKey(privateKeyResource)));
    return jwt.serialize();
  }

  private static PrivateKey privateKey(String resource) throws Exception {
    var pem = new String(new ClassPathResource(resource).getInputStream().readAllBytes(), UTF_8);
    var base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    return KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
  }
}
