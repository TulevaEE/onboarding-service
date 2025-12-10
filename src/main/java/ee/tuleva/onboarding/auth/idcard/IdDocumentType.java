package ee.tuleva.onboarding.auth.idcard;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.OID.*;

import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException;
import java.util.Arrays;
import java.util.List;

public enum IdDocumentType {
  ESTONIAN_CITIZEN_ID_CARD(List.of(POLICE_V1 + "1", POLICE_V2 + "1")),
  EUROPEAN_CITIZEN_ID_CARD(List.of(POLICE_V1 + "2", POLICE_V2 + "2")),
  DIGITAL_ID_CARD(List.of(POLICE_V1 + "3")),
  E_RESIDENT_DIGITAL_ID_CARD(List.of(POLICE_V1 + "4", POLICE_V2 + "6")), // V2 changed: 4→6
  LONG_TERM_RESIDENCE_CARD(List.of(POLICE_V1 + "5", POLICE_V2 + "3")), // V2 changed: 5→3
  TEMPORARY_RESIDENCE_CARD(List.of(POLICE_V1 + "6", POLICE_V2 + "4")), // V2 changed: 6→4
  EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD(
      List.of(POLICE_V1 + "7", POLICE_V2 + "5")), // V2 changed: 7→5
  OLD_ID_CARD(List.of(OLD_PREFIX + "1.1")),
  OLD_DIGITAL_ID_CARD(List.of(OLD_PREFIX + "1.2")),
  DIPLOMATIC_ID_CARD(List.of(FOREIGN_AFFAIRS_V1 + "1", FOREIGN_AFFAIRS_V2 + "1"));

  interface OID {
    String POLICE_V1 = "1.3.6.1.4.1.51361.1.1.";
    String POLICE_V2 = "1.3.6.1.4.1.51361.2.1.";
    String FOREIGN_AFFAIRS_V1 = "1.3.6.1.4.1.51455.1.1.";
    String FOREIGN_AFFAIRS_V2 = "1.3.6.1.4.1.51455.2.1.";
    String OLD_PREFIX = "1.3.6.1.4.1.10015.";
  }

  private final List<String> identifiers;

  IdDocumentType(List<String> identifiers) {
    this.identifiers = identifiers;
  }

  public String getFirstIdentifier() {
    return identifiers.getFirst();
  }

  static IdDocumentType findByIdentifier(String identifier) {
    return Arrays.stream(IdDocumentType.values())
        .filter(idDocumentType -> idDocumentType.identifiers.contains(identifier))
        .findFirst()
        .orElseThrow(() -> new UnknownDocumentTypeException(identifier));
  }

  public Boolean isResident() {
    if (List.of(ESTONIAN_CITIZEN_ID_CARD, OLD_ID_CARD).contains(this)) {
      return true;
    } else if (List.of(
            DIPLOMATIC_ID_CARD,
            E_RESIDENT_DIGITAL_ID_CARD,
            EUROPEAN_CITIZEN_FAMILY_MEMBER_RESIDENCE_CARD,
            EUROPEAN_CITIZEN_ID_CARD)
        .contains(this)) {
      return false;
    }
    return null;
  }
}
