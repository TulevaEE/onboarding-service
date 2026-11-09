package ee.tuleva.onboarding.auth.partner;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.CLIENT_ID;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.FIRST_NAME;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.LAST_NAME;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.ClaimJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PartnerAuthProvider implements AuthProvider {
  private static final String SUPPORTED_TOKEN_TYPE = "HANDOVER";
  private final CompositeJwtParser jwtParser;
  private final PrincipalService principalService;
  private final HandoverTokenUses handoverTokenUses;

  public PartnerAuthProvider(
      PublicKey partnerPublicKey1,
      PublicKey partnerPublicKey2,
      @Value("${partner.issuer}") String partnerIssuer,
      @Value("${partner.clientId}") String partnerClientId,
      Clock clock,
      PrincipalService principalService,
      HandoverTokenUses handoverTokenUses) {
    this.jwtParser =
        new CompositeJwtParser(
            handoverTokenParser(partnerPublicKey1, partnerIssuer, partnerClientId, clock),
            handoverTokenParser(partnerPublicKey2, partnerIssuer, partnerClientId, clock));
    this.principalService = principalService;
    this.handoverTokenUses = handoverTokenUses;
  }

  private static JwtParser handoverTokenParser(
      PublicKey partnerPublicKey, String partnerIssuer, String partnerClientId, Clock clock) {
    return Jwts.parser()
        .verifyWith(partnerPublicKey)
        .requireIssuer(partnerIssuer)
        .require(CLIENT_ID.getValue(), partnerClientId)
        .clock(() -> Date.from(clock.instant()))
        .build();
  }

  @Override
  public boolean supports(GrantType grantType) {
    return grantType == PARTNER;
  }

  @Override
  public AuthenticatedPerson authenticate(@Nullable String handoverToken) {
    Claims claims = verifiedClaims(requireNonNull(handoverToken, "Handover token missing"));
    validate(claims);
    markFirstUse(claims);
    Person person = getPersonFromClaims(claims);
    Map<String, String> attributes = getAttributes(claims);

    return principalService.getFrom(person, attributes);
  }

  private Claims verifiedClaims(String handoverToken) {
    try {
      return jwtParser.parseSignedClaims(handoverToken).getPayload();
    } catch (JwtException rejected) {
      Optional<ExpiredJwtException> expired = expiryUnderAnyKey(rejected);
      if (expired.isPresent()) {
        throw expired.get();
      }
      if (signedByPartner(rejected)) {
        throw new HandoverTokenRejectedException(
            "Partner-signed handover token failed its claims: reasons=" + reasonsPerKey(rejected));
      }
      throw new UnverifiableHandoverTokenException(
          "Handover token not verifiable with any partner key: reasons=" + reasonsPerKey(rejected));
    }
  }

  private static boolean signedByPartner(JwtException rejected) {
    return failuresPerKey(rejected).anyMatch(ClaimJwtException.class::isInstance);
  }

  private static Optional<ExpiredJwtException> expiryUnderAnyKey(JwtException rejected) {
    return failuresPerKey(rejected)
        .filter(ExpiredJwtException.class::isInstance)
        .map(ExpiredJwtException.class::cast)
        .findFirst();
  }

  private static String reasonsPerKey(JwtException rejected) {
    return failuresPerKey(rejected).map(Throwable::getMessage).collect(joining(" | "));
  }

  private static Stream<Throwable> failuresPerKey(JwtException rejected) {
    return Stream.concat(Stream.of(rejected), Arrays.stream(rejected.getSuppressed()));
  }

  private void validate(Claims claims) {
    if (!SUPPORTED_TOKEN_TYPE.equalsIgnoreCase(claims.get("tokenType", String.class))) {
      throw new HandoverTokenRejectedException(
          "Unsupported token type: tokenType=" + claims.get("tokenType"));
    }
    if (claims.getExpiration() == null) {
      throw new HandoverTokenRejectedException(
          "Handover token has no expiry: issuer=" + claims.getIssuer());
    }
  }

  private void markFirstUse(Claims claims) {
    String tokenId = claims.getId();
    if (tokenId == null) {
      throw new HandoverTokenRejectedException(
          "Handover token has no token id: issuer=" + claims.getIssuer());
    }
    if (!handoverTokenUses.firstUse(claims.getIssuer(), tokenId)) {
      throw new HandoverTokenRejectedException(
          "Handover token already used: issuer=" + claims.getIssuer() + ", tokenId=" + tokenId);
    }
  }

  private Person getPersonFromClaims(Claims claims) {
    return PersonImpl.builder()
        .personalCode(claims.getSubject())
        .firstName(FIRST_NAME.stringFrom(claims))
        .lastName(LAST_NAME.stringFrom(claims))
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
