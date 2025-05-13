package ee.tuleva.onboarding.auth.partner;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.FIRST_NAME;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.LAST_NAME;
import static java.util.Collections.unmodifiableMap;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
public class PartnerAuthProvider implements AuthProvider {
  private static final String SUPPORTED_TOKEN_TYPE = "HANDOVER";
  private final CompositeJwtParser jwtParser;
  private final PrincipalService principalService;

  @SneakyThrows
  public PartnerAuthProvider(
      PublicKey partnerPublicKey1,
      PublicKey partnerPublicKey2,
      Clock clock,
      PrincipalService principalService) {
    this.jwtParser =
        new CompositeJwtParser(
            Jwts.parser()
                .verifyWith(partnerPublicKey1)
                .clock(() -> Date.from(clock.instant()))
                .build(),
            Jwts.parser()
                .verifyWith(partnerPublicKey2)
                .clock(() -> Date.from(clock.instant()))
                .build());
    this.principalService = principalService;
  }

  @Override
  public boolean supports(GrantType grantType) {
    return grantType == PARTNER;
  }

  @Override
  public AuthenticatedPerson authenticate(String handoverToken) {
    Claims claims = jwtParser.parseSignedClaims(handoverToken).getPayload();
    validate(claims);
    Person person = getPersonFromClaims(claims);
    Map<String, String> attributes = getAttributes(claims);

    return principalService.getFrom(person, attributes);
  }

  private void validate(Claims claims) {
    if (!SUPPORTED_TOKEN_TYPE.equalsIgnoreCase(claims.get("tokenType", String.class))) {
      throw new UnsupportedJwtException("Unsupported token type: " + claims.get("tokenType"));
    }
  }

  private Person getPersonFromClaims(Claims claims) {
    return PersonImpl.builder()
        .personalCode(claims.getSubject())
        .firstName(FIRST_NAME.fromClaims(claims))
        .lastName(LAST_NAME.fromClaims(claims))
        .build();
  }

  private Map<String, String> getAttributes(Claims claims) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("grantType", PARTNER.name());
    attributes.put("issuer", claims.getIssuer());
    attributes.put("partnerAuthenticationMethod", claims.get("authenticationMethod", String.class));
    attributes.put("partnerSigningMethod", claims.get("signingMethod", String.class));
    attributes.put("phoneNumber", claims.get("phoneNumber", String.class));
    attributes.put("documentNumber", claims.get("documentNumber", String.class));
    return unmodifiableMap(attributes);
  }
}
