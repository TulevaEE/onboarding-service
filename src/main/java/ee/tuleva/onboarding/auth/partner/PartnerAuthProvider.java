package ee.tuleva.onboarding.auth.partner;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.FIRST_NAME;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.LAST_NAME;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class PartnerAuthProvider implements AuthProvider {

  private final JwtParser jwtParser;
  private final PrincipalService principalService;

  // TODO: use a trust store for the public key
  @SneakyThrows
  public PartnerAuthProvider(
      @Value("${partner.publicKey}") Resource partnerPublicKey,
      Clock clock,
      PrincipalService principalService) {
    byte[] publicKeyBytes = partnerPublicKey.getInputStream().readAllBytes();
    PublicKey publicKey = toPublicKey(publicKeyBytes);
    this.jwtParser =
        Jwts.parserBuilder()
            .setSigningKey(publicKey)
            .setClock(() -> Date.from(clock.instant()))
            .build();
    this.principalService = principalService;
  }

  @Override
  public boolean supports(GrantType grantType) {
    return grantType == PARTNER;
  }

  @Override
  public AuthenticatedPerson authenticate(String handoverToken) {
    Person person = getPersonFromToken(handoverToken);
    return principalService.getFrom(person, Map.of());
  }

  private Person getPersonFromToken(String token) {
    Claims claims = jwtParser.parseClaimsJws(token).getBody();
    return PersonImpl.builder()
        .personalCode(claims.getSubject())
        .firstName(FIRST_NAME.fromClaims(claims))
        .lastName(LAST_NAME.fromClaims(claims))
        .build();
  }

  @SneakyThrows
  private PublicKey toPublicKey(byte[] publicKeyBytes) {
    String key = new String(publicKeyBytes);
    String publicKeyPEM =
        key.replace("-----BEGIN PUBLIC KEY-----", "")
            .replaceAll(System.lineSeparator(), "")
            .replace("-----END PUBLIC KEY-----", "");

    byte[] encodedKey = Base64.getDecoder().decode(publicKeyPEM);
    var keySpec = new X509EncodedKeySpec(encodedKey);
    var keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(keySpec);
  }
}
