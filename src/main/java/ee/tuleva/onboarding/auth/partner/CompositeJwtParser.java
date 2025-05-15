package ee.tuleva.onboarding.auth.partner;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeJwtParser {

  private final List<JwtParser> parsers = new ArrayList<>();

  public CompositeJwtParser(JwtParser... parsers) {
    Collections.addAll(this.parsers, parsers);
  }

  public Jws<Claims> parseSignedClaims(String token) throws JwtException {
    JwtException primaryException = null;
    for (JwtParser parser : parsers) {
      try {
        return parser.parseSignedClaims(token);
      } catch (JwtException e) {
        log.debug("Parser {} failed for token={}", parser, token, e);
        if (primaryException == null) {
          primaryException = e;
        } else {
          primaryException.addSuppressed(e);
        }
      }
    }
    if (primaryException != null) {
      throw primaryException;
    }
    throw new IllegalArgumentException("No parser available or token missing");
  }
}
