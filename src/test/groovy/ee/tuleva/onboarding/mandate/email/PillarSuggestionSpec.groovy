package ee.tuleva.onboarding.mandate.email

import spock.lang.Specification
import spock.lang.Unroll

class PillarSuggestionSpec extends Specification {

    @Unroll
    def "suggests membership and other pillar"() {
        when:
        def pillarSuggestion = new PillarSuggestion(2, isSecondPillarActive, isSecondPillarFullyConverted, isMember)

        then:
        pillarSuggestion.suggestPillar() == suggestPillar
        pillarSuggestion.suggestMembership() == suggestMembership

        where:
        isSecondPillarActive | isSecondPillarFullyConverted | isMember || suggestPillar | suggestMembership
        false                | false                        | false    || true          | false
        false                | false                        | true     || true          | false
        true                 | false                        | false    || true          | false
        true                 | false                        | true     || true          | false
        true                 | true                         | false    || false         | true
        true                 | true                         | true     || false         | false
    }
}
