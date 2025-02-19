package ee.tuleva.onboarding.error.converter

import spock.lang.Specification

class ErrorAttributesConverterSpec extends Specification {

  def converter = new ErrorAttributesConverter()

  def "Converts global errors into an ErrorsResponse"() {
    given:
    def errorAttributes = [
      "timestamp": "2017-04-20T10:37:35Z",
      "status"   : 500,
      "error"    : "Internal Server Error",
      "exception": "ee.tuleva.exception.SampleException",
      "path"     : "/v1/users",
      "message"  : "Error message"
    ]

    when:
    def errorsResponse = converter.convert(errorAttributes)
    def error = errorsResponse.errors.first()

    then:
    error.code == 'Internal Server Error'
    error.message == 'Error message'
    error.path == null
    error.arguments == []
  }

}
