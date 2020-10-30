package ee.tuleva.onboarding.mandate.email

import spock.lang.Specification
import spock.lang.Unroll

class SecondPillarSuggestionSpec extends Specification {

    @Unroll
    def "suggests membership and 3rd pillar for 2nd pillar mandates"() {
        when:
        def pillarSuggestion = new SecondPillarSuggestion(isThirdPillarActive, isThirdPillarFullyConverted, isMember)

        then:
        pillarSuggestion.showShortMessage() == showShortMessage
        pillarSuggestion.suggestMembershipIfOtherPillarInactive() == suggestMembershipIfOtherPillarInactive
        pillarSuggestion.suggestMembershipIfOtherPillarFullyConverted() == suggestMembershipIfOtherPillarFullyConverted
        pillarSuggestion.suggestOtherPillar() == suggestOtherPillar

        where:
        isThirdPillarActive | isThirdPillarFullyConverted | isMember || showShortMessage | suggestMembershipIfOtherPillarInactive | suggestMembershipIfOtherPillarFullyConverted | suggestOtherPillar
        false               | false                       | false    || false            | true                                   | false                                        | false
        false               | false                       | true     || true             | false                                  | false                                        | false
        true                | false                       | false    || false            | false                                  | false                                        | true
        true                | false                       | true     || false            | false                                  | false                                        | false
        true                | true                        | false    || false            | false                                  | true                                         | false
        true                | true                        | true     || true             | false                                  | false                                        | false
    }
}
