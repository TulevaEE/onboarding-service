package ee.tuleva.onboarding.mandate.application

import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.application.ApplicationType.*

class ApplicationDetailsSpec extends Specification {

  def "WithdrawalApplicationDetails throw exception on wrong type"() {
    when:
    WithdrawalApplicationDetails.builder().type(type).build()
    then:
    thrown(IllegalArgumentException)
    where:
    type     | _
    null     | _
    TRANSFER | _
  }

  def "TransferApplicationDetails throw exception on wrong type"() {
    when:
    TransferApplicationDetails.builder().type(type).build()
    then:
    thrown(IllegalArgumentException)
    where:
    type             | _
    null             | _
    WITHDRAWAL       | _
    EARLY_WITHDRAWAL | _
  }
}


