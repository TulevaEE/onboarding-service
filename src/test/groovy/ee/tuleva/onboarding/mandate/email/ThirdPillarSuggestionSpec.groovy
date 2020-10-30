package ee.tuleva.onboarding.mandate.email

import spock.lang.Specification
import spock.lang.Unroll

class ThirdPillarSuggestionSpec extends Specification {

    @Unroll
    def "suggests membership and 2nd pillar for 3rd pillar mandates"() {
        when:
        def pillarSuggestion = new ThirdPillarSuggestion(isSecondPillarActive, isSecondPillarFullyConverted, isMember)

        then:
        pillarSuggestion.suggestMembershipIfOtherPillarInactive() == suggestMembershipIfOtherPillarInactive
        pillarSuggestion.suggestMembershipIfOtherPillarFullyConverted() == suggestMembershipIfOtherPillarFullyConverted
        pillarSuggestion.suggestOtherPillar() == suggestOtherPillar

        where:
        isSecondPillarActive | isSecondPillarFullyConverted | isMember || suggestMembershipIfOtherPillarInactive | suggestMembershipIfOtherPillarFullyConverted | suggestOtherPillar
        false                | false                        | false    || true                                   | false                                        | false
        false                | false                        | true     || false                                  | false                                        | false
        true                 | false                        | false    || false                                  | false                                        | true
        true                 | false                        | true     || false                                  | false                                        | false
        true                 | true                         | false    || false                                  | true                                         | false
        true                 | true                         | true     || false                                  | false                                        | false
    }
}
