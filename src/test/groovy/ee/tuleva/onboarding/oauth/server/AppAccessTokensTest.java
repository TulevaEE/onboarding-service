package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.authority.Authority.CONNECTED_APP_ACCESS_TOKEN_TYPE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.BadJwtException;

class AppAccessTokensTest {

  private static final String ISSUER = "http://localhost:9000";
  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
  private static final UUID GRANT_ID = UUID.fromString("5b0c1d2e-3f4a-4b5c-8d6e-7f8a9b0c1d2e");

  private final SigningKeys signingKeys =
      new SigningKeys(
          new OAuthProperties(new ClassPathResource("oauth/test-signing-key.pem"), Map.of()));
  private final ECKey signingKey =
      (ECKey) signingKeys.get(new JWKSelector(new JWKMatcher.Builder().build()), null).getFirst();

  private final AppAccessTokens appAccessTokens =
      new AppAccessTokens(signingKeys, ISSUER, Clock.fixed(NOW, UTC));

  @Test
  void decodesAnAccessTokenTulevaIssuedForItsApi() throws Exception {
    var jwt = appAccessTokens.decode(accessToken(validClaims().build()));

    assertThat(jwt.getClaimAsString("grant_id")).isEqualTo(GRANT_ID.toString());
  }

  @Test
  void refusesAnAccessTokenForTheAudienceOfTheDraftConnectApi() throws Exception {
    var token = accessToken(validClaims().audience(ISSUER + "/connect/v1").build());

    assertThatThrownBy(() -> appAccessTokens.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesAnAccessTokenFromAnotherIssuer() throws Exception {
    var token = accessToken(validClaims().issuer("https://issuer.example").build());

    assertThatThrownBy(() -> appAccessTokens.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesAnAccessTokenWhoseTenMinutesAreOver() throws Exception {
    var issuedAt = NOW.minus(Duration.ofMinutes(12));
    var token =
        accessToken(
            validClaims()
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(Duration.ofMinutes(10))))
                .build());

    assertThatThrownBy(() -> appAccessTokens.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesATokenSignedWithOurKeyThatIsNotAnAccessToken() throws Exception {
    var token = signed(header(JWSAlgorithm.ES384, JOSEObjectType.JWT), validClaims().build());

    assertThatThrownBy(() -> appAccessTokens.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesAnAccessTokenSignedWithAnyOtherKey() throws Exception {
    var token =
        signed(
            header(JWSAlgorithm.ES384, CONNECTED_APP_ACCESS_TOKEN_TYPE),
            validClaims().build(),
            new ECDSASigner(
                EcPem.privateKey(new ClassPathResource("oauth/test-client-private-key.pem"))));

    assertThatThrownBy(() -> appAccessTokens.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesAnRsaSignedTokenEvenWhenItCallsItselfAnAccessToken() throws Exception {
    var token =
        signed(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(CONNECTED_APP_ACCESS_TOKEN_TYPE))
                .build(),
            validClaims().build(),
            new RSASSASigner(rsaPrivateKey()));

    assertThatThrownBy(() -> appAccessTokens.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void recognizesARequestThatPresentsAnAccessToken() throws Exception {
    assertThat(AppAccessTokens.arePresentedBy(bearer(accessToken(validClaims().build())))).isTrue();
  }

  @Test
  void recognizesAnAccessTokenWhateverTheCaseOfTheBearerScheme() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(AUTHORIZATION, "bearer " + accessToken(validClaims().build()));

    assertThat(AppAccessTokens.arePresentedBy(request)).isTrue();
  }

  @Test
  void doesNotTakeOtherBearerTokensForAccessTokens() throws Exception {
    var plainJwt = signed(header(JWSAlgorithm.ES384, JOSEObjectType.JWT), validClaims().build());

    assertThat(AppAccessTokens.arePresentedBy(bearer(plainJwt))).isFalse();
    assertThat(AppAccessTokens.arePresentedBy(bearer("not-a-jwt"))).isFalse();
    assertThat(AppAccessTokens.arePresentedBy(new MockHttpServletRequest())).isFalse();
  }

  private JWTClaimsSet.Builder validClaims() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject(UUID.randomUUID().toString())
        .audience(ISSUER + "/v1")
        .issueTime(Date.from(NOW))
        .notBeforeTime(Date.from(NOW))
        .expirationTime(Date.from(NOW.plus(Duration.ofMinutes(10))))
        .claim("scope", List.of("balances:read"))
        .claim("grant_id", GRANT_ID.toString());
  }

  private String accessToken(JWTClaimsSet claims) throws Exception {
    return signed(header(JWSAlgorithm.ES384, CONNECTED_APP_ACCESS_TOKEN_TYPE), claims);
  }

  private JWSHeader header(JWSAlgorithm algorithm, String type) {
    return header(algorithm, new JOSEObjectType(type));
  }

  private JWSHeader header(JWSAlgorithm algorithm, JOSEObjectType type) {
    return new JWSHeader.Builder(algorithm).type(type).keyID(signingKey.getKeyID()).build();
  }

  private String signed(JWSHeader header, JWTClaimsSet claims) throws Exception {
    return signed(header, claims, new ECDSASigner(signingKey));
  }

  private static String signed(JWSHeader header, JWTClaimsSet claims, JWSSigner signer)
      throws Exception {
    var jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }

  private static MockHttpServletRequest bearer(String token) {
    var request = new MockHttpServletRequest();
    request.addHeader(AUTHORIZATION, "Bearer " + token);
    return request;
  }

  private static RSAPrivateKey rsaPrivateKey() throws Exception {
    var pem =
        new String(
            new ClassPathResource("oauth/test-rsa-private-key.pem")
                .getInputStream()
                .readAllBytes());
    var der =
        Base64.getDecoder()
            .decode(
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", ""));
    return (RSAPrivateKey)
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
  }
}
