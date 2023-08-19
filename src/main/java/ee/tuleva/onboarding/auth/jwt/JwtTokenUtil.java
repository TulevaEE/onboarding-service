package ee.tuleva.onboarding.auth.jwt;

import static java.time.temporal.ChronoUnit.MINUTES;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.Key;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
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

  public String getUsernameFromToken(String token) {
    return getClaimFromToken(token, Claims::getSubject);
  }

  public List<String> getAuthoritiesFromToken(String token) {
    return getClaimFromToken(token, (claims) -> claims.get("authorities", List.class));
  }

  public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = getAllClaimsFromToken(token);
    return claimsResolver.apply(claims);
  }

  private Claims getAllClaimsFromToken(String token) {
    return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
  }

  public String generateToken(
      AuthenticatedPerson person, Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .setClaims(
            Map.of(
                "firstName", person.getFirstName(),
                "lastName", person.getLastName(),
                "authorities", authorities.stream().map(GrantedAuthority::getAuthority).toList()))
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
        .setClaims(Map.of("authorities", List.of()))
        .setIssuedAt(Date.from(clock.instant()))
        .setExpiration(Date.from(clock.instant().plus(JWT_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }
}
