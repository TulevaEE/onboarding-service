package ee.tuleva.onboarding.kyb.screener;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class CompanyPartiesMetadata {

  private static final String PARTIES = "parties";
  private static final String CODE = "code";
  private static final String NATURAL_PERSON = "naturalPerson";
  private static final String ROLES = "roles";

  private static final Comparator<KybRelatedPerson> PARTY_ORDER =
      comparing(CompanyPartiesMetadata::code, nullsLast(naturalOrder()))
          .thenComparing(KybRelatedPerson::naturalPerson)
          .thenComparing(KybRelatedPerson::boardMember)
          .thenComparing(KybRelatedPerson::shareholder)
          .thenComparing(KybRelatedPerson::beneficialOwner);

  private CompanyPartiesMetadata() {}

  static Map<String, Object> of(final List<KybRelatedPerson> relatedPersons) {
    return Map.of(
        PARTIES,
        relatedPersons.stream()
            .sorted(PARTY_ORDER)
            .map(CompanyPartiesMetadata::partyMetadata)
            .toList());
  }

  private static Map<String, Object> partyMetadata(KybRelatedPerson person) {
    var metadata = new HashMap<String, Object>();
    var code = code(person);
    if (code != null) {
      metadata.put(CODE, code);
    }
    metadata.put(NATURAL_PERSON, person.naturalPerson());
    metadata.put(ROLES, CompanyPartyRole.heldBy(person).stream().map(Enum::name).toList());
    return Map.copyOf(metadata);
  }

  private static @Nullable String code(KybRelatedPerson person) {
    var personalCode = person.personalCode();
    return personalCode == null ? null : personalCode.value();
  }
}
