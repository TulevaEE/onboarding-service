package ee.tuleva.onboarding.auth.jwt;

import static ee.tuleva.onboarding.auth.jwt.CustomClaims.*;
import static java.time.temporal.ChronoUnit.MINUTES;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.Key;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenUtil {

  private final Duration JWT_TOKEN_VALIDITY = Duration.of(30, MINUTES);
  private final Key signingKey;
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
    return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
  }

  public String generateToken(
      AuthenticatedPerson person, Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .setClaims(
            Map.of(
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
        .setExpiration(Date.from(clock.instant().plus(JWT_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public String generateServiceToken() {
    Map<String, Object> claims = new HashMap<>();
    return Jwts.builder()
        .setClaims(claims)
        .setSubject("onboarding-service")
        .setClaims(Map.of(AUTHORITIES.value, List.of()))
        .setIssuedAt(Date.from(clock.instant()))
        .setExpiration(Date.from(clock.instant().plus(JWT_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

  public Map<String, String> getAttributesFromToken(String jwtToken) {
    return ATTRIBUTES.fromClaims(getAllClaimsFromToken(jwtToken));
  }

  public List<String> getAuthoritiesFromToken(String jwtToken) {
    return AUTHORITIES.fromClaims(getAllClaimsFromToken(jwtToken));
  }
}
