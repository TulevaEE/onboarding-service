package ee.tuleva.onboarding.mandate.application

import spock.lang.Specification

class ApplicationStatusSpec extends Specification {

  def "isComplete is true only for COMPLETE"() {
    expect:
    ApplicationStatus.COMPLETE.isComplete()
    !ApplicationStatus.PENDING.isComplete()
    !ApplicationStatus.FAILED.isComplete()
  }

  def "isPending is true only for PENDING"() {
    expect:
    ApplicationStatus.PENDING.isPending()
    !ApplicationStatus.COMPLETE.isPending()
    !ApplicationStatus.FAILED.isPending()
  }
}
