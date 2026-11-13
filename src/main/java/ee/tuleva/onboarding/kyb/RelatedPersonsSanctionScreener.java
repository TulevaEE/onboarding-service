package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybRelationshipRoles.BENEFICIAL_OWNER_ROLE;
import static ee.tuleva.onboarding.kyb.KybRelationshipRoles.BOARD_MEMBER_ROLES;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class RelatedPersonsSanctionScreener {

  private static final String NATURAL_PERSON_TYPE = "F";
  private static final PersonalCodeValidator PERSONAL_CODE_VALIDATOR = new PersonalCodeValidator();

  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final UserRepository userRepository;

  void screen(
      String registryCode,
      List<CompanyRelationship> relationships,
      BeneficialOwners beneficialOwners) {
    List<Optional<PersonImpl>> candidates =
        Stream.concat(
                relationships.stream()
                    .filter(RelatedPersonsSanctionScreener::isBoardMemberOrBeneficialOwner)
                    .map(
                        relationship ->
                            person(
                                relationship.personalCode(),
                                relationship.firstName(),
                                relationship.lastName())),
                beneficialOwners.owners().stream().map(RelatedPersonsSanctionScreener::person))
            .toList();
    Map<String, PersonImpl> byCode = new LinkedHashMap<>();
    candidates.stream()
        .flatMap(Optional::stream)
        .forEach(person -> byCode.putIfAbsent(person.getPersonalCode(), person));
    warnAboutUnscreenable(
        registryCode,
        candidates.stream().filter(Optional::isEmpty).count() + beneficialOwners.hiddenCount());
    byCode.values().forEach(person -> screenPerson(registryCode, person));
  }

  private static void warnAboutUnscreenable(String registryCode, long unscreenableCount) {
    if (unscreenableCount > 0) {
      log.warn(
          "Related person entries without an Estonian personal code or a name are not sanction/PEP screened: registryCode={}, entryCount={}",
          registryCode,
          unscreenableCount);
    }
  }

  private void screenPerson(String registryCode, PersonImpl person) {
    try {
      userRepository
          .findByPersonalCode(person.getPersonalCode())
          .ifPresentOrElse(
              sanctionAndPepScreener::addSanctionAndPepCheckIfMissing,
              () ->
                  sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(
                      person, sanctionAndPepScreener.recordedCitizenships(person)));
    } catch (RuntimeException e) {
      log.error(
          "Related person sanction/PEP screening failed: registryCode={}, personalCode={}",
          registryCode,
          person.getPersonalCode(),
          e);
    }
  }

  private static boolean isBoardMemberOrBeneficialOwner(CompanyRelationship relationship) {
    return NATURAL_PERSON_TYPE.equals(relationship.personType())
        && (BOARD_MEMBER_ROLES.contains(relationship.roleCode())
            || BENEFICIAL_OWNER_ROLE.equals(relationship.roleCode()));
  }

  private static Optional<PersonImpl> person(BeneficialOwner owner) {
    return person(owner.personalCode(), owner.firstName(), owner.lastName());
  }

  private static Optional<PersonImpl> person(
      @Nullable String personalCode, @Nullable String firstName, @Nullable String lastName) {
    if (personalCode == null
        || firstName == null
        || lastName == null
        || !PERSONAL_CODE_VALIDATOR.isValid(personalCode)) {
      return Optional.empty();
    }
    return Optional.of(new PersonImpl(personalCode, firstName, lastName));
  }
}
