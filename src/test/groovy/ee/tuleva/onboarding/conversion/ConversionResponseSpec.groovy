package ee.tuleva.onboarding.conversion

import spock.lang.Specification

import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted

class ConversionResponseSpec extends Specification {

  def "is Tuleva second pillar selected"() {
    given:
    ConversionResponse.Conversion secondPillar = Mock()
    def conversionResponse = new ConversionResponse(
        secondPillar,
        notFullyConverted().thirdPillar,
        BigDecimal.ZERO
    )
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
    def conversionResponse = new ConversionResponse(
        secondPillar,
        notFullyConverted().thirdPillar,
        BigDecimal.ZERO
    )

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
    def conversionResponse = new ConversionResponse(
        notFullyConverted().secondPillar,
        thirdPillar,
        BigDecimal.ZERO
    )

    when:
    def answer = conversionResponse.isThirdPillarFullyConverted()

    then:
    answer == expectedAnswer

    where:
    thirdPillar                     | expectedAnswer
    notFullyConverted().thirdPillar | false
    fullyConverted().thirdPillar    | true
  }

  def "is partially converted when transfers or selection are partial"() {
    expect:
    ConversionResponse.Conversion.builder()
        .transfersPartial(transfersPartial).selectionPartial(selectionPartial).build()
        .isPartiallyConverted() == expected

    where:
    transfersPartial | selectionPartial | expected
    true             | false            | true
    false            | true             | true
    true             | true             | true
    false            | false            | false
  }

  def "pillar level partial conversion follows each pillar's conversion"() {
    given:
    def response = ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder().transfersPartial(true).build())
        .thirdPillar(ConversionResponse.Conversion.builder().selectionPartial(false).build())
        .build()

    expect:
    response.isSecondPillarPartiallyConverted()
    !response.isThirdPillarPartiallyConverted()
  }

  def "pillar level weighted average fees delegate to each pillar"() {
    given:
    def response = ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder().weightedAverageFee(0.0049).build())
        .thirdPillar(ConversionResponse.Conversion.builder().weightedAverageFee(0.0034).build())
        .build()

    expect:
    response.getSecondPillarWeightedAverageFee() == 0.0049
    response.getThirdPillarWeightedAverageFee() == 0.0034
  }
}
