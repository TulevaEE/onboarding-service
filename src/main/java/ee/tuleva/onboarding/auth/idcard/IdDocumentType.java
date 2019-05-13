package ee.tuleva.onboarding.auth.idcard;

import java.util.Arrays;

public enum IdDocumentType {

    ESTONIAN_CITIZEN_ID_CARD(1),
    EUROPEAN_CITIZEN_ID_CARD(2),
    DIGITAL_ID_CARD(3),
    E_RESIDENT_DIGITAL_ID_CARD(4),
    LONG_TERM_RESIDENCE_CARD(5),
    TEMPORARY_RESIDENCE_CARD(6),
    EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD(7),
    OLD_ID_CARD("1.3.6.1.4.1.10015.1.1"),
    OLD_DIGITAL_ID_CARD("1.3.6.1.4.1.10015.1.2"),
    DIPLOMATIC_ID_CARD(1, true),
    UNKNOWN(null);

    private static final String POLICE_PREFIX = "1.3.6.1.4.1.51361.1.1.";
    private static final String FOREIGN_AFFAIRS_PREFIX = "1.3.6.1.4.1.51455.1.1.";

    private final String identifier;

    IdDocumentType(String identifier) {
        this.identifier = identifier;
    }

    IdDocumentType(int number) {
        this(number, false);
    }

    IdDocumentType(int number, boolean foreignAffairs) {
        if (foreignAffairs) {
            identifier = FOREIGN_AFFAIRS_PREFIX + number;
        } else {
            identifier = POLICE_PREFIX + number;
        }
    }

    static IdDocumentType findByIdentifier(String identifier) {
        return Arrays.stream(IdDocumentType.values())
            .filter(d -> d.identifier.equals(identifier))
            .findFirst().orElse(UNKNOWN);
    }
}
