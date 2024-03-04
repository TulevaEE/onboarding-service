package ee.tuleva.onboarding.auth.jwt;

import static ee.tuleva.onboarding.auth.jwt.CustomClaims.*;
import static java.time.temporal.ChronoUnit.*;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import io.jsonwebtoken.*;
import java.security.Key;
import java.security.KeyStore;
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
  private final JwtParser jwtParser;
  private final Clock clock;

  @SneakyThrows
  public JwtTokenUtil(
      @Value("${jwt.keystore}") Resource keystoreResource,
      @Value("${jwt.keystore-password}") char[] keystorePassword,
      Clock clock) {
    this.clock = clock;
    final var keystore = KeyStore.getInstance("PKCS12");
    keystore.load(keystoreResource.getInputStream(), keystorePassword);
    this.signingKey = keystore.getKey("jwt", keystorePassword);
    this.jwtParser =
        Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .setClock(() -> Date.from(clock.instant()))
            .build();
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
    return jwtParser.parseClaimsJws(token).getBody();
  }

  public String generateAccessToken(
      AuthenticatedPerson person, Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .setClaims(
            Map.of(
                TOKEN_TYPE.value,
                TokenType.ACCESS,
                FIRST_NAME.value,
                person.getFirstName(),
                LAST_NAME.value,
                person.getLastName(),
                ATTRIBUTES.value,
                person.getAttributes(),
                AUTHORITIES.value,
                authorities.stream().map(GrantedAuthority::getAuthority).toList()))
        .setSubject(person.getPersonalCode())
        .setIssuedAt(Date.from(clock.instant()))
        .setExpiration(Date.from(clock.instant().plus(ACCESS_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public String generateRefreshToken(
      AuthenticatedPerson person, Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .setClaims(
            Map.of(
                TOKEN_TYPE.value,
                TokenType.REFRESH,
                FIRST_NAME.value,
                person.getFirstName(),
                LAST_NAME.value,
                person.getLastName(),
                ATTRIBUTES.value,
                person.getAttributes(),
                AUTHORITIES.value,
                authorities.stream().map(GrantedAuthority::getAuthority).toList()))
        .setSubject(person.getPersonalCode())
        .setIssuedAt(Date.from(clock.instant()))
        .setExpiration(Date.from(clock.instant().plus(REFRESH_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public String generateServiceToken() {
    return Jwts.builder()
        .setClaims(Map.of(AUTHORITIES.value, List.of()))
        .setSubject("onboarding-service")
        .setIssuedAt(Date.from(clock.instant()))
        .setExpiration(Date.from(clock.instant().plus(ACCESS_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public Map<String, String> getAttributesFromToken(String jwtToken) {
    return ATTRIBUTES.fromClaims(getAllClaimsFromToken(jwtToken));
  }

  public List<String> getAuthoritiesFromToken(String jwtToken) {
    return AUTHORITIES.fromClaims(getAllClaimsFromToken(jwtToken));
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
