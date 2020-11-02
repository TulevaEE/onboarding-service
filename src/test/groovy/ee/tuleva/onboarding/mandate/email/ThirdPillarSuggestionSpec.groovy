package ee.tuleva.onboarding.mandate.email

import spock.lang.Specification
import spock.lang.Unroll

class ThirdPillarSuggestionSpec extends Specification {

    @Unroll
    def "suggests membership and 3rd pillar for 2nd pillar mandates"() {
        when:
        def pillarSuggestion = new ThirdPillarSuggestion(isThirdPillarActive, isThirdPillarFullyConverted, isMember)

        then:
        pillarSuggestion.showShortMessage() == showShortMessage
        pillarSuggestion.suggestMembershipIfPillarInactive() == suggestMembershipIfPillarInactive
        pillarSuggestion.suggestMembershipIfFullyConverted() == suggestMembershipIfFullyConverted
        pillarSuggestion.suggestPillar() == suggestPillar

        where:
        isThirdPillarActive | isThirdPillarFullyConverted | isMember || showShortMessage | suggestMembershipIfPillarInactive | suggestMembershipIfFullyConverted | suggestPillar
        false               | false                       | false    || false            | true                              | false                             | false
        false               | false                       | true     || true             | false                             | false                             | false
        true                | false                       | false    || false            | false                             | false                             | true
        true                | false                       | true     || false            | false                             | false                             | false
        true                | true                        | false    || false            | false                             | true                              | false
        true                | true                        | true     || true             | false                             | false                             | false
    }
}
