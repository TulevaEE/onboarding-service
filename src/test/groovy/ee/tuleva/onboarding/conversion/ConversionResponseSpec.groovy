package ee.tuleva.onboarding.conversion

import spock.lang.Specification

import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted

class ConversionResponseSpec extends Specification {

  def "is Tuleva second pillar selected"() {
    given:
    ConversionResponse.Conversion secondPillar = Mock()
    def conversionResponse = new ConversionResponse(secondPillar, notFullyConverted().thirdPillar)
    secondPillar.isSelectionComplete() >> selectionComplete

    when:
    def answer = conversionResponse.isSecondPillarSelected()

    then:
    answer == selectionComplete

    where:
    selectionComplete | expectedAnswer
    true              | true
    false             | false
  }

  def "is second pillar fully converted to Tuleva"() {
    given:
    def conversionResponse = new ConversionResponse(secondPillar, notFullyConverted().thirdPillar)

    when:
    def answer = conversionResponse.isSecondPillarFullyConverted()

    then:
    answer == expectedAnswer

    where:
    secondPillar                     | expectedAnswer
    notFullyConverted().secondPillar | false
    fullyConverted().secondPillar    | true
  }

  def "is third pillar fully converted to Tuleva"() {
    given:
    def conversionResponse = new ConversionResponse(notFullyConverted().secondPillar, thirdPillar)

    when:
    def answer = conversionResponse.isThirdPillarFullyConverted()

    then:
    answer == expectedAnswer

    where:
    thirdPillar                     | expectedAnswer
    notFullyConverted().thirdPillar | false
    fullyConverted().thirdPillar    | true
  }

}
