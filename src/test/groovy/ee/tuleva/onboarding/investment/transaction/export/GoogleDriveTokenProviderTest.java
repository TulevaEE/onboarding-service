package ee.tuleva.onboarding.investment.transaction.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GoogleDriveTokenProviderTest {

  @Test
  void getAccessToken_exchangesJwtForAccessToken() throws Exception {
    var keyPair = generateKeyPair();
    var serviceAccountJson = buildServiceAccountJson(keyPair);
    var restClient = mockTokenExchange("test-access-token");
    var clock = Clock.fixed(Instant.parse("2026-02-16T10:00:00Z"), ZoneId.of("UTC"));

    var provider = new GoogleDriveTokenProvider(serviceAccountJson, restClient, clock);

    assertThat(provider.getAccessToken()).isEqualTo("test-access-token");
  }

  @Test
  void getAccessToken_createsValidJwtWithCorrectClaims() throws Exception {
    var keyPair = generateKeyPair();
    var serviceAccountJson = buildServiceAccountJson(keyPair);
    var capturedJwt = new String[1];

    var restClient = mockTokenExchangeCapturingJwt("token-value", capturedJwt);
    var clock = Clock.fixed(Instant.parse("2026-02-16T10:00:00Z"), ZoneId.of("UTC"));

    var provider = new GoogleDriveTokenProvider(serviceAccountJson, restClient, clock);
    provider.getAccessToken();

    var jws = JWSObject.parse(capturedJwt[0]);
    JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
    assertThat(jws.verify(verifier)).isTrue();

    var claims = jws.getPayload().toJSONObject();
    assertThat(claims.get("iss")).isEqualTo("test@project.iam.gserviceaccount.com");
    assertThat(claims.get("scope")).isEqualTo("https://www.googleapis.com/auth/drive.file");
    assertThat(claims.get("aud")).isEqualTo("https://oauth2.googleapis.com/token");
  }

  @Test
  void getAccessToken_returnsCachedTokenWhenNotExpired() throws Exception {
    var keyPair = generateKeyPair();
    var serviceAccountJson = buildServiceAccountJson(keyPair);
    var restClient = mockTokenExchange("cached-token");
    var clock = Clock.fixed(Instant.parse("2026-02-16T10:00:00Z"), ZoneId.of("UTC"));

    var provider = new GoogleDriveTokenProvider(serviceAccountJson, restClient, clock);

    provider.getAccessToken();
    provider.getAccessToken();

    verify(restClient, times(1)).post();
  }

  @Test
  void getAccessToken_refreshesExpiredToken() throws Exception {
    var keyPair = generateKeyPair();
    var serviceAccountJson = buildServiceAccountJson(keyPair);
    var start = Instant.parse("2026-02-16T10:00:00Z");
    var mutableClock = Clock.offset(Clock.fixed(start, ZoneId.of("UTC")), Duration.ZERO);

    var bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
    when(bodyUriSpec.body(any(String.class))).thenReturn(bodyUriSpec);
    when(bodyUriSpec.retrieve()).thenReturn(responseSpec);

    var firstResponse = Map.of("access_token", "first-token", "expires_in", 3600);
    var secondResponse = Map.of("access_token", "second-token", "expires_in", 3600);

    var restClient = mock(RestClient.class);
    when(restClient.post()).thenReturn(bodyUriSpec);
    when(responseSpec.body(Map.class)).thenReturn(firstResponse, secondResponse);

    var provider = new GoogleDriveTokenProvider(serviceAccountJson, restClient, mutableClock);

    assertThat(provider.getAccessToken()).isEqualTo("first-token");

    var expiredClock = Clock.offset(Clock.fixed(start, ZoneId.of("UTC")), Duration.ofHours(2));
    var providerAfterExpiry =
        new GoogleDriveTokenProvider(serviceAccountJson, restClient, expiredClock);

    assertThat(providerAfterExpiry.getAccessToken()).isEqualTo("second-token");
  }

  private static KeyPair generateKeyPair() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String buildServiceAccountJson(KeyPair keyPair) {
    var privateKeyPem = pemEncodePrivateKey(keyPair);
    var json =
        new JSONObject(
            Map.of(
                "type", "service_account",
                "client_email", "test@project.iam.gserviceaccount.com",
                "private_key", privateKeyPem,
                "token_uri", "https://oauth2.googleapis.com/token"));
    return Base64.getEncoder().encodeToString(json.toJSONString().getBytes());
  }

  private static String pemEncodePrivateKey(KeyPair keyPair) {
    var encoded = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
  }

  @SuppressWarnings("unchecked")
  private static RestClient mockTokenExchange(String accessToken) {
    var restClient = mock(RestClient.class);
    var bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(bodyUriSpec);
    when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
    when(bodyUriSpec.body(any(String.class))).thenReturn(bodyUriSpec);
    when(bodyUriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(Map.class))
        .thenReturn(Map.of("access_token", accessToken, "expires_in", 3600));

    return restClient;
  }

  @SuppressWarnings("unchecked")
  private static RestClient mockTokenExchangeCapturingJwt(
      String accessToken, String[] capturedJwt) {
    var restClient = mock(RestClient.class);
    var bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(bodyUriSpec);
    when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
    when(bodyUriSpec.body(any(String.class)))
        .thenAnswer(
            invocation -> {
              String body = invocation.getArgument(0);
              var parts = body.split("&");
              for (var part : parts) {
                if (part.startsWith("assertion=")) {
                  capturedJwt[0] = java.net.URLDecoder.decode(part.substring(10), "UTF-8");
                }
              }
              return bodyUriSpec;
            });
    when(bodyUriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(Map.class))
        .thenReturn(Map.of("access_token", accessToken, "expires_in", 3600));

    return restClient;
  }
}
