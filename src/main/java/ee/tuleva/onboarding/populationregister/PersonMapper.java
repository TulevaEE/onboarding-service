package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.OTHER;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.INACTIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.UNKNOWN;

import ee.tuleva.onboarding.populationregister.PersonResponse.Citizenship;
import ee.tuleva.onboarding.populationregister.PersonResponse.Code;
import ee.tuleva.onboarding.populationregister.PersonResponse.Custody;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;

class PersonMapper {

  private static final String ALIVE_CODE = "E";
  private static final String VALID_CUSTODY_CODE = "H1";
  private static final String PERSONAL_CUSTODY_CODE = "H10";
  private static final String PROPERTY_CUSTODY_CODE = "H20";

  static PopulationRegisterPerson toPerson(PersonResponse response) {
    return new PopulationRegisterPerson(
        require(response.personalCode(), "isikukood"),
        require(response.firstName(), "eesnimi"),
        require(response.lastName(), "perekonnanimi"),
        parseDate(response.dateOfBirth()),
        toStatus(response.status()),
        toCitizenship(response.citizenship()));
  }

  static List<CustodyRight> toCustodyRights(PersonResponse response) {
    List<Custody> custodies = response.custodyRights();
    if (custodies == null) {
      return List.of();
    }
    return custodies.stream().map(PersonMapper::toCustodyRight).toList();
  }

  private static CustodyRight toCustodyRight(Custody custody) {
    return new CustodyRight(
        require(custody.otherPersonCode(), "teineIsikIsikukood"),
        toCustodyType(custody.type()),
        hasCode(custody.status(), VALID_CUSTODY_CODE),
        hasCode(custody.otherPersonStatus(), ALIVE_CODE));
  }

  private static CustodyRight.Type toCustodyType(@Nullable Code type) {
    return switch (type == null ? null : type.code()) {
      case PROPERTY_CUSTODY_CODE -> PROPERTY;
      case PERSONAL_CUSTODY_CODE -> PERSONAL;
      case null, default -> OTHER;
    };
  }

  private static Status toStatus(@Nullable Code status) {
    return switch (status == null ? null : status.code()) {
      case ALIVE_CODE -> ALIVE;
      case null -> UNKNOWN;
      default -> INACTIVE;
    };
  }

  private static @Nullable String toCitizenship(@Nullable Citizenship citizenship) {
    if (citizenship == null || citizenship.country() == null) {
      return null;
    }
    return citizenship.country().name();
  }

  private static boolean hasCode(@Nullable Code value, String expected) {
    return value != null && expected.equals(value.code());
  }

  private static @Nullable LocalDate parseDate(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String require(@Nullable String value, String field) {
    if (value == null) {
      throw new PopulationRegisterException(
          "Population register response missing required field: field=" + field);
    }
    return value;
  }
}
