package ee.tuleva.onboarding.auth.jwt;

import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CustomClaims {
  FIRST_NAME("firstName", String.class),
  LAST_NAME("lastName", String.class),
  ATTRIBUTES("attributes", Map.class),
  AUTHORITIES("authorities", List.class),
  TOKEN_TYPE("tokenType", String.class),
  CLIENT_ID("cid", String.class);

  final String value;
  final Class<?> type;

  public <T> T fromClaims(Claims claims) {
    return (T) claims.get(value, type);
  }
}
