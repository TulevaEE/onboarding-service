package ee.tuleva.onboarding.auth.partner

import spock.lang.Specification
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser

class CompositeJwtParserSpec extends Specification {

  def "should return the first successfully parsed JWS claims"() {
    given:
    JwtParser failingParser = Mock()
    JwtParser successfulParser = Mock()
    Jws<Claims> expectedJws = Mock()
    String validToken = "validToken"

    failingParser.parseSignedClaims(validToken) >> { throw new JwtException("Failed parsing") }
    successfulParser.parseSignedClaims(validToken) >> expectedJws

    CompositeJwtParser parser = new CompositeJwtParser(failingParser, successfulParser)

    when:
    Jws<Claims> result = parser.parseSignedClaims(validToken)

    then:
    result == expectedJws
  }

  def "should throw the first exception if all parsers fail"() {
    given:
    JwtParser parser1 = Mock()
    JwtParser parser2 = Mock()
    String invalidToken = "invalidToken"

    parser1.parseSignedClaims(invalidToken) >> { throw new JwtException("First failure") }
    parser2.parseSignedClaims(invalidToken) >> { throw new JwtException("Second failure") }

    CompositeJwtParser parser = new CompositeJwtParser(parser1, parser2)

    when:
    parser.parseSignedClaims(invalidToken)

    then:
    def e = thrown(JwtException)
    e.message == "First failure"
    e.suppressed[0].message == "Second failure"
  }

  def "should throw an IllegalArgumentException if no parsers are available"() {
    given:
    CompositeJwtParser parser = new CompositeJwtParser()

    when:
    parser.parseSignedClaims("anyToken")

    then:
    thrown(IllegalArgumentException)
  }
}
