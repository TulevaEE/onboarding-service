package ee.tuleva.onboarding.auth.idcard


import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.*

class IdDocumentTypeSpec extends Specification {

    @Unroll
    def "#documentType isResident: #isResident"() {
        expect:
        documentType.isResident() == isResident

        where:
        documentType                                  | isResident
        ESTONIAN_CITIZEN_ID_CARD                      | true
        OLD_ID_CARD                                   | true
        EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD | false
        E_RESIDENT_DIGITAL_ID_CARD                    | false
        EUROPEAN_CITIZEN_ID_CARD                      | false
        DIPLOMATIC_ID_CARD                            | false
        DIGITAL_ID_CARD                               | null
        OLD_DIGITAL_ID_CARD                           | null
        UNKNOWN                                       | null
    }
}
