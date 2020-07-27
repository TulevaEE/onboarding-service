package ee.tuleva.onboarding.conversion

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted

class ConversionResponseSpec extends Specification {

    @Unroll
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

    @Unroll
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