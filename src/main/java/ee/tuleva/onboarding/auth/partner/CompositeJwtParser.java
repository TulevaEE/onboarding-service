package ee.tuleva.onboarding.auth.partner;

import io.jsonwebtoken.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeJwtParser {

  private final List<JwtParser> jwtParsers = new ArrayList<>();

  public CompositeJwtParser(JwtParser... jwtParsers) {
    Collections.addAll(this.jwtParsers, jwtParsers);
  }

  public Jws<Claims> parseSignedClaims(String handoverToken) throws JwtException {
    JwtException lastException = null;
    for (JwtParser jwtParser : jwtParsers) {
      try {
        return jwtParser.parseSignedClaims(handoverToken);
      } catch (JwtException e) {
        log.debug("Failed to parse token", e);
        lastException = e;
      }
    }
    if (lastException != null) {
      throw lastException;
    }
    throw new IllegalArgumentException("No parsers available or no token was provided");
  }
}
