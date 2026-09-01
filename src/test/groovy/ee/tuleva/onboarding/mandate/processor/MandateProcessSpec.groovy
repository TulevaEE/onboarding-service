package ee.tuleva.onboarding.mandate.processor

import spock.lang.Specification

class MandateProcessSpec extends Specification {

  def "getSuccessful returns the successful flag when present"() {
    given:
    def process = MandateProcess.builder().successful(true).build()

    expect:
    process.getSuccessful() == Optional.of(true)
  }

  def "getErrorCode returns the error code when present"() {
    given:
    def process = MandateProcess.builder().errorCode(40551).build()

    expect:
    process.getErrorCode() == Optional.of(40551)
  }
}
