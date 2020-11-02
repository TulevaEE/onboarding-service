package ee.tuleva.onboarding.mandate.email

import spock.lang.Specification
import spock.lang.Unroll

class SecondPillarSuggestionSpec extends Specification {

    @Unroll
    def "suggests membership and 2nd pillar for 3rd pillar mandates"() {
        when:
        def pillarSuggestion = new SecondPillarSuggestion(isSecondPillarActive, isSecondPillarFullyConverted, isMember)

        then:
        pillarSuggestion.suggestMembershipIfPillarInactive() == suggestMembershipIfPillarInactive
        pillarSuggestion.suggestMembershipIfFullyConverted() == suggestMembershipIfPillarFullyConverted
        pillarSuggestion.suggestPillar() == suggestPillar

        where:
        isSecondPillarActive | isSecondPillarFullyConverted | isMember || suggestMembershipIfPillarInactive | suggestMembershipIfPillarFullyConverted | suggestPillar
        false                | false                        | false    || true                              | false                                   | false
        false                | false                        | true     || false                             | false                                   | false
        true                 | false                        | false    || false                             | false                                   | true
        true                 | false                        | true     || false                             | false                                   | false
        true                 | true                         | false    || false                             | true                                    | false
        true                 | true                         | true     || false                             | false                                   | false
    }
}
