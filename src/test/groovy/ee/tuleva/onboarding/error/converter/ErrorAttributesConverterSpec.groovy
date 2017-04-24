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
        "exception": "ee.tuleva.onboarding.user.exception.SaveUserException",
        "path"     : "/v1/users",
        "message"  : "Error saving user, does the user already exist?"
    ]

    when:
    def errorsResponse = converter.convert(errorAttributes)
    def error = errorsResponse.errors.first()

    then:
    error.code == 'SaveUserException'
    error.message == 'Error saving user, does the user already exist?'
    error.path == null
    error.arguments == null
  }

}
