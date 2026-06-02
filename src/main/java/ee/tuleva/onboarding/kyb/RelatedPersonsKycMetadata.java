package ee.tuleva.onboarding.kyb;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RelatedPersonsKycMetadata {

  private static final String INCOMPLETE_PERSONS = "incompletePersons";
  private static final String PERSONAL_CODE = "personalCode";
  private static final String KYC_STATUS = "kycStatus";

  private RelatedPersonsKycMetadata() {}

  public static Map<String, Object> forIncompletePersons(List<KybRelatedPerson> incomplete) {
    if (incomplete.isEmpty()) {
      return Map.of();
    }
    var persons =
        incomplete.stream()
            .map(
                person ->
                    Map.of(
                        PERSONAL_CODE, person.personalCode().toString(),
                        KYC_STATUS, person.kycStatus().name()))
            .toList();
    return Map.of(INCOMPLETE_PERSONS, persons);
  }

  @SuppressWarnings("unchecked")
  public static List<String> incompletePersonalCodes(KybCheck check) {
    var persons =
        (List<Map<String, Object>>) check.metadata().getOrDefault(INCOMPLETE_PERSONS, List.of());
    return persons.stream()
        .map(person -> person.get(PERSONAL_CODE))
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .toList();
  }
}
