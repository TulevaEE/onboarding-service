package ee.tuleva.onboarding.mandate.generic

import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class MandateDtoDeserializerSpec extends Specification {

  JsonMapper jsonMapper = JsonMapper.builder().build()

  def "deserialize reads the mandate id when present"() {
    given:
    def json = '''
        {"id": 123, "details": {"mandateType": "SELECTION", "futureContributionFundIsin": "EE123"}}
        '''

    when:
    MandateDto<?> dto = jsonMapper.readValue(json, MandateDto)

    then:
    dto.id == 123L
  }

  def "deserialize leaves the mandate id unset when absent"() {
    given:
    def json = '''
        {"details": {"mandateType": "SELECTION", "futureContributionFundIsin": "EE123"}}
        '''

    when:
    MandateDto<?> dto = jsonMapper.readValue(json, MandateDto)

    then:
    dto.id == null
  }
}
