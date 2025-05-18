package ee.tuleva.onboarding.auth.jwt;

import static ee.tuleva.onboarding.auth.authority.Authority.PARTNER;
import static ee.tuleva.onboarding.auth.authority.Authority.SERVICE;
import static ee.tuleva.onboarding.auth.jwt.CustomClaims.*;
import static ee.tuleva.onboarding.auth.jwt.TokenType.*;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

import ee.tuleva.onboarding.auth.partner.CompositeJwtParser;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.Key;
import java.security.KeyStore;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenUtil {

  private final Duration ACCESS_TOKEN_VALIDITY = Duration.of(30, MINUTES);
  private final Duration REFRESH_TOKEN_VALIDITY = Duration.of(4, HOURS);
  private final Key signingKey;
  private final CompositeJwtParser jwtParser;
  private final Clock clock;

  @SneakyThrows
  public JwtTokenUtil(
      @Value("${jwt.keystore}") Resource keystoreResource,
      @Value("${jwt.keystore-password}") char[] keystorePassword,
      @Value("${partner.issuer}") String partnerIssuer,
      @Value("${partner.clientId}") String partnerClientId,
      PublicKey partnerPublicKey1,
      PublicKey partnerPublicKey2,
      Clock clock) {
    this.clock = clock;
    KeyStore keystore = KeyStore.getInstance("PKCS12");
    keystore.load(keystoreResource.getInputStream(), keystorePassword);
    this.signingKey = keystore.getKey("jwt", keystorePassword);
    PublicKey ourPublicKey = keystore.getCertificate("jwt").getPublicKey();

    this.jwtParser =
        new CompositeJwtParser(
            Jwts.parser().verifyWith(ourPublicKey).clock(() -> Date.from(clock.instant())).build(),
            Jwts.parser()
                .verifyWith(partnerPublicKey1)
                .requireIssuer(partnerIssuer)
                .require(CLIENT_ID.value, partnerClientId)
                .require(TOKEN_TYPE.value, HANDOVER.name())
                .clock(() -> Date.from(clock.instant()))
                .build(),
            Jwts.parser()
                .verifyWith(partnerPublicKey2)
                .requireIssuer(partnerIssuer)
                .require(CLIENT_ID.value, partnerClientId)
                .require(TOKEN_TYPE.value, HANDOVER.name())
                .clock(() -> Date.from(clock.instant()))
                .build());
  }

  public Person getPersonFromToken(String token) {
    final var claims = getAllClaimsFromToken(token);
    final var personalCode = claims.getSubject();
    final String firstName = FIRST_NAME.fromClaims(claims);
    final String lastName = LAST_NAME.fromClaims(claims);
    return PersonImpl.builder()
        .personalCode(personalCode)
        .firstName(firstName)
        .lastName(lastName)
        .build();
  }

  private Claims getAllClaimsFromToken(String token) {
    // When token is expired, this throws ExpiredJwtException, which is handled in
    // ErrorHandlingControllerAdvice
    return jwtParser.parseSignedClaims(token).getPayload();
  }

  public String generateAccessToken(
      AuthenticatedPerson person, Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .claims(
            Map.of(
                TOKEN_TYPE.value,
                ACCESS,
                FIRST_NAME.value,
                person.getFirstName(),
                LAST_NAME.value,
                person.getLastName(),
                ATTRIBUTES.value,
                person.getAttributes(),
                AUTHORITIES.value,
                authorities.stream().map(GrantedAuthority::getAuthority).toList()))
        .subject(person.getPersonalCode())
        .issuedAt(Date.from(clock.instant()))
        .expiration(Date.from(clock.instant().plus(ACCESS_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public String generateRefreshToken(
      AuthenticatedPerson person, Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .claims(
            Map.of(
                TOKEN_TYPE.value, REFRESH,
                FIRST_NAME.value, person.getFirstName(),
                LAST_NAME.value, person.getLastName(),
                ATTRIBUTES.value, person.getAttributes(),
                AUTHORITIES.value,
                    authorities.stream().map(GrantedAuthority::getAuthority).toList()))
        .subject(person.getPersonalCode())
        .issuedAt(Date.from(clock.instant()))
        .expiration(Date.from(clock.instant().plus(REFRESH_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public String generateServiceToken() {
    return Jwts.builder()
        .claims(Map.of(TOKEN_TYPE.value, ACCESS, AUTHORITIES.value, List.of(SERVICE)))
        .subject("onboarding-service")
        .issuedAt(Date.from(clock.instant()))
        .expiration(Date.from(clock.instant().plus(ACCESS_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public Map<String, String> getAttributesFromToken(String jwtToken) {
    return ATTRIBUTES.fromClaims(getAllClaimsFromToken(jwtToken));
  }

  public List<String> getAuthorities(String jwtToken) {
    if (HANDOVER.equals(getTypeFromToken(jwtToken))) {
      return List.of(PARTNER);
    }
    List<String> authorities = AUTHORITIES.fromClaims(getAllClaimsFromToken(jwtToken));
    return authorities != null ? authorities : List.of();
  }

  public TokenType getTypeFromToken(String jwtToken) {
    return TokenType.valueOf(TOKEN_TYPE.fromClaims(getAllClaimsFromToken(jwtToken)));
  }

  public static Map<String, String> getExpiredTokenErrorResponse() {
    return Map.of(
        "error", "TOKEN_EXPIRED",
        "error_description", "The token is expired.");
  }
}
