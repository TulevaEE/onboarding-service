package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.OTHER;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.INVALID;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.INACTIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.UNKNOWN;
import static ee.tuleva.onboarding.user.Names.formatted;

import ee.tuleva.onboarding.country.CountryCodes;
import ee.tuleva.onboarding.populationregister.PersonResponse.Citizenship;
import ee.tuleva.onboarding.populationregister.PersonResponse.Code;
import ee.tuleva.onboarding.populationregister.PersonResponse.Custody;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
class PersonMapper {

  private static final String ALIVE_CODE = "E";
  private static final String VALID_CUSTODY_CODE = "H1";
  private static final String PERSONAL_CUSTODY_CODE = "H10";
  private static final String PROPERTY_CUSTODY_CODE = "H20";

  static PopulationRegisterPerson toPerson(PersonResponse response) {
    return new PopulationRegisterPerson(
        require(response.personalCode(), "isikukood"),
        formatted(require(response.firstName(), "eesnimi")),
        formatted(require(response.lastName(), "perekonnanimi")),
        parseDate(response.dateOfBirth()),
        toStatus(response.status()),
        toCitizenship(response.citizenship()),
        toCitizenships(response));
  }

  static List<CustodyRight> toCustodyRights(PersonResponse response) {
    List<Custody> custodies = response.custodyRights();
    if (custodies == null) {
      return List.of();
    }
    return custodies.stream().map(PersonMapper::toCustodyRight).toList();
  }

  // A custody row without a counterpart personal code (e.g. an institutional guardian identified
  // by registry code) can never become a co-parent link — skip it instead of failing the whole
  // guardian listing.
  static List<Guardian> toGuardians(PersonResponse response) {
    List<Custody> custodies = response.custodyRights();
    if (custodies == null) {
      return List.of();
    }
    return custodies.stream()
        .filter(custody -> custody.otherPersonCode() != null)
        .map(PersonMapper::toGuardian)
        .toList();
  }

  private static Guardian toGuardian(Custody custody) {
    return new Guardian(
        require(custody.otherPersonCode(), "teineIsikIsikukood"),
        toCustodyType(custody.type()),
        hasCode(custody.status(), VALID_CUSTODY_CODE) ? VALID : INVALID,
        toStatus(custody.otherPersonStatus()));
  }

  private static CustodyRight toCustodyRight(Custody custody) {
    return new CustodyRight(
        require(custody.otherPersonCode(), "teineIsikIsikukood"),
        toCustodyType(custody.type()),
        hasCode(custody.status(), VALID_CUSTODY_CODE) ? VALID : INVALID,
        toStatus(custody.otherPersonStatus()),
        capitalizeName(custody.otherPersonFirstName()),
        capitalizeName(custody.otherPersonLastName()));
  }

  // The register returns names in uppercase (JÕEORG); present them the same way the rest of the
  // app stores names (Jõeorg), matching ParentChildLinkRegistrationService.
  private static @Nullable String capitalizeName(@Nullable String name) {
    return name == null ? null : formatted(name);
  }

  private static CustodyRight.Type toCustodyType(@Nullable Code type) {
    return switch (type == null ? null : type.code()) {
      case PROPERTY_CUSTODY_CODE -> PROPERTY_CUSTODY;
      case PERSONAL_CUSTODY_CODE -> PERSONAL_CUSTODY;
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

  private static List<String> toCitizenships(PersonResponse response) {
    var all =
        Stream.concat(
            Stream.of(response.citizenship()),
            response.citizenships() == null ? Stream.of() : response.citizenships().stream());
    return all.map(PersonMapper::toCitizenship).filter(Objects::nonNull).distinct().toList();
  }

  private static @Nullable String toCitizenship(@Nullable Citizenship citizenship) {
    Code country = citizenship == null ? null : citizenship.country();
    if (country == null) {
      return null;
    }
    String alpha2 = CountryCodes.numericToAlpha2(country.code());
    if (alpha2 == null) {
      return null;
    }
    if (isUnmappedNumericCode(alpha2)) {
      log.warn("Dropping unmapped population register citizenship: code={}", country.code());
      return null;
    }
    return alpha2;
  }

  private static boolean isUnmappedNumericCode(String code) {
    return code.chars().allMatch(Character::isDigit);
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
