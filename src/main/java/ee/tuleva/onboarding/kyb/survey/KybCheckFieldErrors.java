package ee.tuleva.onboarding.kyb.survey;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.RelatedPersonsKycMetadata;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class KybCheckFieldErrors {

  private KybCheckFieldErrors() {}

  private record FieldError(String field, ValidationError error) {}

  private static final String KYC_MESSAGE = "Isikusamasuse tuvastamine on lõpetamata";
  private static final String USER_KYC_MESSAGE = "Sinu isikusamasuse tuvastamine on lõpetamata";

  static Map<String, List<ValidationError>> collectErrorsByField(
      List<KybCheck> checks, String userPersonalCode, List<RelatedPersonData> relatedPersons) {
    return checks.stream()
        .filter(check -> !check.success())
        .flatMap(check -> fieldErrorsFor(check, userPersonalCode, relatedPersons))
        .collect(groupingBy(FieldError::field, mapping(FieldError::error, toList())));
  }

  static <T> ValidatedField<T> validatedField(@Nullable T value, List<ValidationError> errors) {
    return errors.isEmpty()
        ? ValidatedField.valid(value)
        : ValidatedField.withErrors(value, errors);
  }

  static List<RelatedPersonData> dedupedByPersonalCode(List<CompanyRelationship> relationships) {
    return relationships.stream()
        .map(r -> new RelatedPersonData(r.personalCode(), formatName(r)))
        .collect(
            toMap(RelatedPersonData::personalCode, identity(), (a, b) -> a, LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  // Projects a failed check to its client-facing field error(s). Each arm assigns a curated,
  // client-safe code; the internal KybCheckType never reaches the wire. SANCTION and PEP collapse
  // to one opaque code+message so a sanctions hit cannot be told apart from a PEP flag.
  // RELATED_PERSONS_KYC needs runtime context (which persons are incomplete, and whether one is
  // the onboarding user) and can yield two codes, so it is delegated to its own method.
  private static Stream<FieldError> fieldErrorsFor(
      KybCheck check, String userPersonalCode, List<RelatedPersonData> relatedPersons) {
    return switch (check.type()) {
      case COMPANY_ACTIVE ->
          Stream.of(fieldError("status", "COMPANY_ACTIVE", "Ettevõte ei ole aktiivne"));
      case HIGH_RISK_NACE ->
          Stream.of(fieldError("naceCode", "UNSUPPORTED_NACE", "See tegevusala ei ole toetatud"));
      case COMPANY_SANCTION, COMPANY_PEP ->
          Stream.of(fieldError("name", "UNSERVICEABLE", "Ettevõtet ei ole võimalik teenindada"));
      case RELATED_PERSONS_KYC -> relatedPersonsKycErrors(check, userPersonalCode, relatedPersons);
      case COMPANY_LEGAL_FORM ->
          Stream.of(fieldError("legalForm", "COMPANY_LEGAL_FORM", "Ainult OÜ on toetatud"));
      case COMPANY_REGISTERED_IN_ESTONIA ->
          Stream.of(
              fieldError(
                  "address",
                  "COMPANY_REGISTERED_IN_ESTONIA",
                  "Ettevõte ei ole registreeritud Eestis"));
      case COMPANY_STRUCTURE,
          SOLE_MEMBER_OWNERSHIP,
          DUAL_MEMBER_OWNERSHIP,
          SINGLE_BOARD_MEMBER_OWNERSHIP ->
          Stream.of(
              fieldError(
                  "relatedPersons",
                  "COMPANY_STRUCTURE",
                  "Ettevõtte omandistruktuur ei ole toetatud"));
      case DATA_CHANGED, SELF_CERTIFICATION, COMPANY_AGE -> Stream.of();
    };
  }

  private static FieldError fieldError(String field, String code, String message) {
    return new FieldError(field, new ValidationError(code, message));
  }

  private static FieldError fieldError(
      String field, String code, String message, List<RelatedPersonData> persons) {
    return new FieldError(field, new ValidationError(code, message, persons));
  }

  private static Stream<FieldError> relatedPersonsKycErrors(
      KybCheck check, String userPersonalCode, List<RelatedPersonData> relatedPersons) {
    var incompletePersonalCodes = RelatedPersonsKycMetadata.incompletePersonalCodes(check);
    var userIncomplete = incompletePersonalCodes.contains(userPersonalCode);
    var otherPersonalCodes =
        incompletePersonalCodes.stream().filter(code -> !code.equals(userPersonalCode)).toList();

    var errors = new ArrayList<FieldError>();
    if (userIncomplete) {
      errors.add(fieldError("relatedPersons", "USER_KYC", USER_KYC_MESSAGE));
    }
    if (!otherPersonalCodes.isEmpty()) {
      errors.add(
          fieldError(
              "relatedPersons",
              "OTHER_RELATED_PERSONS_KYC",
              KYC_MESSAGE,
              personsFor(otherPersonalCodes, relatedPersons)));
    }
    if (errors.isEmpty()) {
      errors.add(fieldError("relatedPersons", "OTHER_RELATED_PERSONS_KYC", KYC_MESSAGE));
    }
    return errors.stream();
  }

  private static List<RelatedPersonData> personsFor(
      List<String> personalCodes, List<RelatedPersonData> relatedPersons) {
    var personsByPersonalCode =
        relatedPersons.stream()
            .filter(person -> person.personalCode() != null)
            .collect(toMap(RelatedPersonData::personalCode, identity(), (a, b) -> a));
    return personalCodes.stream()
        .distinct()
        .map(
            personalCode ->
                personsByPersonalCode.getOrDefault(
                    personalCode, new RelatedPersonData(personalCode, null)))
        .toList();
  }

  private static String formatName(CompanyRelationship r) {
    if (r.firstName() != null && r.lastName() != null) {
      return r.firstName() + " " + r.lastName();
    }
    return r.lastName();
  }
}
