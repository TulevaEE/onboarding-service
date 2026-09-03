package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.SELECTION

class SelectionMandateDetailsSpec extends Specification {

  def "getApplicationType is SELECTION"() {
    expect:
    new SelectionMandateDetails("isin").getApplicationType() == SELECTION
  }
}
