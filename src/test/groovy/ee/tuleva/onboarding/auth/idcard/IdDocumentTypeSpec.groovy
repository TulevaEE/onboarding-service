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
    }

    @Unroll
    def "findByIdentifier maps OID '#oid' to #expectedType"() {
        when:
        def result = IdDocumentType.findByIdentifier(oid)

        then:
        result == expectedType

        where:
        oid                            | expectedType
        "1.3.6.1.4.1.51361.1.1.1"     | ESTONIAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.2.1.1"     | ESTONIAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.1.1.2"     | EUROPEAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.2.1.2"     | EUROPEAN_CITIZEN_ID_CARD
        "1.3.6.1.4.1.51361.1.1.3"     | DIGITAL_ID_CARD
        "1.3.6.1.4.1.51361.1.1.4"     | E_RESIDENT_DIGITAL_ID_CARD
        "1.3.6.1.4.1.51361.2.1.6"     | E_RESIDENT_DIGITAL_ID_CARD
        "1.3.6.1.4.1.51361.1.1.5"     | LONG_TERM_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.3"     | LONG_TERM_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.1.1.6"     | TEMPORARY_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.4"     | TEMPORARY_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.1.1.7"     | EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD
        "1.3.6.1.4.1.51361.2.1.5"     | EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD
        "1.3.6.1.4.1.10015.1.1"       | OLD_ID_CARD
        "1.3.6.1.4.1.10015.1.2"       | OLD_DIGITAL_ID_CARD
        "1.3.6.1.4.1.51455.1.1.1"     | DIPLOMATIC_ID_CARD
        "1.3.6.1.4.1.51455.2.1.1"     | DIPLOMATIC_ID_CARD
    }
}
