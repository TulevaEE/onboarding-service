package ee.tuleva.onboarding.pillar

import spock.lang.Specification
import spock.lang.Unroll

class PillarSpec extends Specification {

  @Unroll
  def "fromInt(#intValue) returns #pillar and toInt() returns it back"() {
    expect:
    Pillar.fromInt(intValue) == pillar
    pillar.toInt() == intValue

    where:
    intValue | pillar
    2        | Pillar.SECOND
    3        | Pillar.THIRD
  }

  def "fromInt throws for a value that matches no pillar"() {
    when:
    Pillar.fromInt(4)

    then:
    thrown(IllegalArgumentException)
  }
}
