package ee.tuleva.onboarding.nudge

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.nudge.Known.NO
import static ee.tuleva.onboarding.nudge.Known.UNKNOWN
import static ee.tuleva.onboarding.nudge.Known.YES

class KnownSpec extends Specification {

  @Unroll
  def "#left or #right is #expected"() {
    expect:
    left.or(right) == expected

    where:
    left    | right   || expected
    YES     | YES     || YES
    YES     | NO      || YES
    YES     | UNKNOWN || YES
    NO      | YES     || YES
    UNKNOWN | YES     || YES
    NO      | NO      || NO
    NO      | UNKNOWN || UNKNOWN
    UNKNOWN | NO      || UNKNOWN
    UNKNOWN | UNKNOWN || UNKNOWN
  }
}
