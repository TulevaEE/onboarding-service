package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KybCheckFieldErrorsTest {

  private static final String PERSONAL_CODE = "38888888888";

  private static final List<RelatedPersonData> BOARD_MEMBER_WITH_TWO_OWNERS =
      List.of(
          new RelatedPersonData(PERSONAL_CODE, "Jaan Tamm"),
          new RelatedPersonData("38888888881", "Mari Maasikas"),
          new RelatedPersonData("38888888882", "Peeter Kask"));

  private static final List<RelatedPersonData> SAMPLE_RELATED_PERSONS =
      List.of(new RelatedPersonData(PERSONAL_CODE, "Jaan Tamm"));

  @Test
  void collectErrorsByField_codesOtherRelatedPersonsKycWithPersons() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(
                        Map.of("personalCode", "38888888881", "kycStatus", "PENDING"),
                        Map.of("personalCode", "38888888882", "kycStatus", "UNKNOWN")))));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(
            checks, PERSONAL_CODE, BOARD_MEMBER_WITH_TWO_OWNERS);

    assertThat(errorsByField.get("relatedPersons"))
        .containsExactly(
            new ValidationError(
                "OTHER_RELATED_PERSONS_KYC",
                "Isikusamasuse tuvastamine on lõpetamata",
                List.of(
                    new RelatedPersonData("38888888881", "Mari Maasikas"),
                    new RelatedPersonData("38888888882", "Peeter Kask"))));
  }

  @Test
  void collectErrorsByField_codesOtherRelatedPersonsKycWithoutNameWhenPersonNotInRelatedPersons() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(Map.of("personalCode", "38501010005", "kycStatus", "PENDING")))));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField.get("relatedPersons"))
        .containsExactly(
            new ValidationError(
                "OTHER_RELATED_PERSONS_KYC",
                "Isikusamasuse tuvastamine on lõpetamata",
                List.of(new RelatedPersonData("38501010005", null))));
  }

  @Test
  void collectErrorsByField_codesUserKycWithoutNameWhenOwnKycIncomplete() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(Map.of("personalCode", PERSONAL_CODE, "kycStatus", "PENDING")))));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField.get("relatedPersons"))
        .containsExactly(
            new ValidationError("USER_KYC", "Sinu isikusamasuse tuvastamine on lõpetamata"));
  }

  @Test
  void collectErrorsByField_splitsUserAndOtherKycIntoSeparateErrors() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(
                        Map.of("personalCode", PERSONAL_CODE, "kycStatus", "PENDING"),
                        Map.of("personalCode", "38888888881", "kycStatus", "PENDING"),
                        Map.of("personalCode", "38888888882", "kycStatus", "UNKNOWN")))));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(
            checks, PERSONAL_CODE, BOARD_MEMBER_WITH_TWO_OWNERS);

    assertThat(errorsByField.get("relatedPersons"))
        .containsExactly(
            new ValidationError("USER_KYC", "Sinu isikusamasuse tuvastamine on lõpetamata"),
            new ValidationError(
                "OTHER_RELATED_PERSONS_KYC",
                "Isikusamasuse tuvastamine on lõpetamata",
                List.of(
                    new RelatedPersonData("38888888881", "Mari Maasikas"),
                    new RelatedPersonData("38888888882", "Peeter Kask"))));
  }

  @Test
  void collectErrorsByField_collapsesSanctionAndPepToIndistinguishableNameError() {
    var sanctionErrors = nameErrorsFor(COMPANY_SANCTION);
    var pepErrors = nameErrorsFor(COMPANY_PEP);

    assertThat(sanctionErrors).isEqualTo(pepErrors);
    assertThat(sanctionErrors)
        .containsExactly(
            new ValidationError("UNSERVICEABLE", "Ettevõtet ei ole võimalik teenindada"));
  }

  private List<ValidationError> nameErrorsFor(KybCheckType type) {
    var checks =
        List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()), new KybCheck(type, false, Map.of()));
    return KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS)
        .get("name");
  }

  @Test
  void collectErrorsByField_carriesClientCodesForStatusAndLegalFormChecks() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, false, Map.of()),
            new KybCheck(COMPANY_LEGAL_FORM, false, Map.of()));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField.get("status"))
        .containsExactly(new ValidationError("COMPANY_ACTIVE", "Ettevõte ei ole aktiivne"));
    assertThat(errorsByField.get("legalForm"))
        .containsExactly(new ValidationError("COMPANY_LEGAL_FORM", "Ainult OÜ on toetatud"));
  }

  @Test
  void collectErrorsByField_returnsAddressErrorWhenNotRegisteredInEstonia() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, false, Map.of("countryCode", "DE")));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField.get("address"))
        .containsExactly(
            new ValidationError(
                "COMPANY_REGISTERED_IN_ESTONIA", "Ettevõte ei ole registreeritud Eestis"));
  }

  @Test
  void collectErrorsByField_returnsNoAddressErrorWhenRegisteredInEstonia() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, true, Map.of("countryCode", "EE")));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField.getOrDefault("address", List.of())).isEmpty();
  }

  @Test
  void collectErrorsByField_returnsNoErrorsWhenAllChecksPassed() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of()));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField).isEmpty();
  }

  @Test
  void collectErrorsByField_toleratesDataChangedCheck() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of("changes", List.of("status changed"))));

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, PERSONAL_CODE, SAMPLE_RELATED_PERSONS);

    assertThat(errorsByField.getOrDefault("name", List.of())).isEmpty();
  }

  @Test
  void dedupedByPersonalCode_deduplicatesRelatedPersons() {
    var relationships =
        List.of(
            new CompanyRelationship(
                "F",
                "JUHL",
                "Juhatuse liige",
                "Jaan",
                "Tamm",
                PERSONAL_CODE,
                null,
                null,
                null,
                null,
                null,
                "EST"),
            new CompanyRelationship(
                "F",
                "OSAN",
                "Osanik",
                "JAAN",
                "TAMM",
                PERSONAL_CODE,
                null,
                null,
                null,
                new BigDecimal("100.00"),
                "Osaluse kaudu",
                "EST"));

    var relatedPersons = KybCheckFieldErrors.dedupedByPersonalCode(relationships);

    assertThat(relatedPersons).containsExactly(new RelatedPersonData(PERSONAL_CODE, "Jaan Tamm"));
  }

  @Test
  void validatedField_returnsValidWhenNoErrors() {
    var field = KybCheckFieldErrors.validatedField("Test OÜ", List.of());

    assertThat(field.value()).isEqualTo("Test OÜ");
    assertThat(field.errors()).isEmpty();
  }

  @Test
  void validatedField_carriesErrorsWhenPresent() {
    var error = new ValidationError("COMPANY_ACTIVE", "Ettevõte ei ole aktiivne");

    var field = KybCheckFieldErrors.validatedField("Test OÜ", List.of(error));

    assertThat(field.value()).isEqualTo("Test OÜ");
    assertThat(field.errors()).containsExactly(error);
  }
}
