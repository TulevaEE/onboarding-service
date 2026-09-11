package ee.tuleva.onboarding.auth.jwt;

import static java.util.Objects.requireNonNull;

import io.jsonwebtoken.Claims;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public enum CustomClaims {
  FIRST_NAME("firstName"),
  LAST_NAME("lastName"),
  ATTRIBUTES("attributes"),
  AUTHORITIES("authorities"),
  TOKEN_TYPE("tokenType"),
  CLIENT_ID("cid"),
  ROLE("role");

  final String value;

  public String stringFrom(Claims claims) {
    return requireNonNull(claims.get(value, String.class), "Missing claim: claim=" + value);
  }

  public @Nullable Object rawFrom(Claims claims) {
    return claims.get(value);
  }
}
