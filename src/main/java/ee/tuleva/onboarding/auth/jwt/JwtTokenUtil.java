package ee.tuleva.onboarding.auth.jwt;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.jsonwebtoken.Jwts;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

import static java.time.temporal.ChronoUnit.MINUTES;

@Component
public class JwtTokenUtil {

  private final Duration JWT_TOKEN_VALIDITY = Duration.of(30, MINUTES);
  private final Key signingKey;
  private final Clock clock;

  @SneakyThrows
  public JwtTokenUtil(@Value("${jwt.keystore}") Resource keystoreResource,
                      @Value("${jwt.keystore-password}") char[] keystorePassword,
                      Clock clock) {
    this.clock = clock;
    final var keystore = KeyStore.getInstance("PKCS12");
    keystore.load(keystoreResource.getInputStream(), keystorePassword);
    this.signingKey = keystore.getKey("jwt", keystorePassword);
  }

  public String generateToken(AuthenticatedPerson person,
                              Collection<? extends GrantedAuthority> authorities) {
    return Jwts.builder()
        .setClaims(Map.of(
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
    return Jwts.builder().setClaims(claims)
        .setSubject("onboarding-service")
        .setClaims(Map.of("authorities", List.of()))
        .setIssuedAt(Date.from(clock.instant()))
        .setExpiration(Date.from(clock.instant().plus(JWT_TOKEN_VALIDITY)))
        .signWith(signingKey)
        .compact();
  }

}
