package ee.tuleva.onboarding.investment.transaction.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
class GoogleDriveTokenProvider {

  private static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
  private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
  private static final long TOKEN_LIFETIME_SECONDS = 3600;
  private static final long REFRESH_MARGIN_SECONDS = 60;

  private final String clientEmail;
  private final RSAPrivateKey privateKey;
  private final RestClient restClient;
  private final Clock clock;

  private String cachedToken;
  private Instant tokenExpiry = Instant.EPOCH;

  GoogleDriveTokenProvider(String base64ServiceAccountJson, RestClient restClient, Clock clock) {
    this.restClient = restClient;
    this.clock = clock;

    var serviceAccount = parseServiceAccountJson(base64ServiceAccountJson);
    this.clientEmail = serviceAccount.get("client_email").asText();
    this.privateKey = parsePrivateKey(serviceAccount.get("private_key").asText());
  }

  String getAccessToken() {
    if (cachedToken != null && Instant.now(clock).isBefore(tokenExpiry)) {
      return cachedToken;
    }
    return refreshToken();
  }

  @SuppressWarnings("unchecked")
  private String refreshToken() {
    var jwt = createSignedJwt();
    var body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt;

    Map<String, Object> response =
        restClient
            .post()
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(Map.class);

    cachedToken = (String) response.get("access_token");
    int expiresIn = (int) response.get("expires_in");
    tokenExpiry = Instant.now(clock).plusSeconds(expiresIn - REFRESH_MARGIN_SECONDS);

    return cachedToken;
  }

  private String createSignedJwt() {
    try {
      var now = Instant.now(clock);
      var claims =
          new Payload(
              Map.of(
                  "iss", clientEmail,
                  "scope", DRIVE_FILE_SCOPE,
                  "aud", TOKEN_URI,
                  "iat", now.getEpochSecond(),
                  "exp", now.getEpochSecond() + TOKEN_LIFETIME_SECONDS));

      var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
      var jws = new JWSObject(header, claims);
      jws.sign(new RSASSASigner(privateKey));

      return jws.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to create signed JWT", e);
    }
  }

  private static JsonNode parseServiceAccountJson(String base64Json) {
    try {
      var json = new String(Base64.getDecoder().decode(base64Json));
      return new ObjectMapper().readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse service account JSON", e);
    }
  }

  private static RSAPrivateKey parsePrivateKey(String pem) {
    try {
      var stripped =
          pem.replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      var keyBytes = Base64.getDecoder().decode(stripped);
      var keySpec = new PKCS8EncodedKeySpec(keyBytes);
      return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse RSA private key", e);
    }
  }
}
