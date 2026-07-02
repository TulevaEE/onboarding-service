package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
class KybScreeningIntegrationTest {

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010002");
  private static final Instant NOW = Instant.parse("2025-06-01T00:00:00Z");

  @Autowired private KybScreeningService kybScreeningService;
  @Autowired private AmlCheckRepository amlCheckRepository;

  @Autowired private SavingsFundOnboardingRepository onboardingRepository;
  @Autowired private JsonMapper objectMapper;
  @Autowired private Clock clock;
  @Autowired private EntityManager entityManager;
  @MockitoBean private PepAndSanctionCheckService sanctionCheckService;

  @BeforeEach
  void setUp() {
    when(sanctionCheckService.matchCompany(any()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
  }

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void singlePersonCompanyWithValidOwnershipAndCompletedKycCreatesSuccessfulChecks() {
    var person = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    var data = companyWith(person);

    var results = kybScreeningService.screen(data);

    assertThat(results).hasSize(11).allMatch(KybCheck::success);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var types = amlChecks.stream().map(AmlCheck::getType).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            KYB_COMPANY_STRUCTURE,
            KYB_COMPANY_ACTIVE,
            KYB_SOLE_MEMBER_OWNERSHIP,
            KYB_RELATED_PERSONS_KYC,
            KYB_COMPANY_SANCTION,
            KYB_COMPANY_PEP,
            KYB_HIGH_RISK_NACE,
            KYB_COMPANY_LEGAL_FORM,
            KYB_COMPANY_REGISTERED_IN_ESTONIA,
            KYB_SELF_CERTIFICATION,
            KYB_DATA_CHANGED);
    assertThat(amlChecks).allMatch(AmlCheck::isSuccess);
  }

  @Test
  void singlePersonCompanyWithInvalidOwnershipCreatesFailedCheck() {
    var person =
        kybPerson()
            .personalCode(PERSONAL_CODE)
            .boardMember(true)
            .shareholder(true)
            .ownershipPercent(BigDecimal.valueOf(100))
            .kycStatus(KybKycStatus.COMPLETED)
            .build();
    var data = companyWith(person);

    var results = kybScreeningService.screen(data);

    assertThat(results).filteredOn(c -> !c.success()).hasSize(1);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var failedCheck =
        amlChecks.stream().filter(c -> c.getType() == KYB_SOLE_MEMBER_OWNERSHIP).findFirst();
    assertThat(failedCheck).isPresent();
    assertThat(failedCheck.get().isSuccess()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void dataChangedCheckDetectsChangesOnRerun() {
    var person = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    var data = companyWith(person);

    kybScreeningService.screen(data);

    var changedData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            CompanyStatus.L,
            List.of(person),
            new SelfCertification(true, true, true),
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            null,
            List.of());

    var secondResults = kybScreeningService.screen(changedData);

    var dataChangedCheck =
        secondResults.stream().filter(c -> c.type() == KybCheckType.DATA_CHANGED).findFirst();
    assertThat(dataChangedCheck).isPresent();
    assertThat(dataChangedCheck.get().success()).isFalse();

    var changes = (List<Map<String, Object>>) dataChangedCheck.get().metadata().get("changes");
    assertThat(changes).isNotEmpty();
    assertThat(changes).anyMatch(c -> "COMPANY_ACTIVE".equals(c.get("check")));
  }

  @Test
  void relatedPersonWithRejectedKycCreatesFailedKycCheck() {
    var person = boardMemberOwner(PERSONAL_CODE, 100.0).kycStatus(KybKycStatus.REJECTED).build();
    var data = companyWith(person);

    var results = kybScreeningService.screen(data);

    var kycCheck =
        results.stream().filter(c -> c.type() == KybCheckType.RELATED_PERSONS_KYC).findFirst();
    assertThat(kycCheck).isPresent();
    assertThat(kycCheck.get().success()).isFalse();

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var kycAmlCheck =
        amlChecks.stream().filter(c -> c.getType() == KYB_RELATED_PERSONS_KYC).findFirst();
    assertThat(kycAmlCheck).isPresent();
    assertThat(kycAmlCheck.get().isSuccess()).isFalse();
  }

  @Test
  void companyWithUnidentifiedRelatedPersonIsBlockedWithoutCrashing() {
    var unidentified =
        boardMemberOwner((PersonalCode) null, 100.0).kycStatus(KybKycStatus.UNKNOWN).build();
    var data = companyWith(unidentified);

    var results = kybScreeningService.screen(data);

    var structureCheck =
        results.stream().filter(c -> c.type() == KybCheckType.COMPANY_STRUCTURE).findFirst();
    assertThat(structureCheck).isPresent();
    assertThat(structureCheck.get().success()).isFalse();

    var kycCheck =
        results.stream().filter(c -> c.type() == KybCheckType.RELATED_PERSONS_KYC).findFirst();
    assertThat(kycCheck).isPresent();
    assertThat(kycCheck.get().success()).isFalse();

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var structureAmlCheck =
        amlChecks.stream().filter(c -> c.getType() == KYB_COMPANY_STRUCTURE).findFirst();
    assertThat(structureAmlCheck).isPresent();
    assertThat(structureAmlCheck.get().isSuccess()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void rescreeningAnUnchangedSoleOwnerCompanyDoesNotFlagDataChanged() {
    // Same company, screened twice with byte-identical input. The ownership and age metadata must
    // survive the AmlCheck jsonb round-trip so KybDataChangeDetector sees no change. AML #78: a
    // BigDecimal ownershipPercent was reloaded as a Double, so DATA_CHANGED fired for ~95% of
    // companies on every screening.
    var person = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person),
            new SelfCertification(true, true, true),
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            LocalDate.now(clock).minusYears(3),
            List.of());

    kybScreeningService.screen(data);
    // Force a fresh read so the second screening deserializes metadata from JSON, exactly as a
    // separate production screening transaction does — not the in-transaction L1 cache.
    entityManager.flush();
    entityManager.clear();
    var secondResults = kybScreeningService.screen(data);

    var dataChanged =
        secondResults.stream()
            .filter(c -> c.type() == KybCheckType.DATA_CHANGED)
            .findFirst()
            .orElseThrow();
    assertThat(dataChanged.success()).isTrue();
    assertThat((List<Map<String, Object>>) dataChanged.metadata().get("changes")).isEmpty();
  }

  @Test
  void missingBeneficialOwnerEvidenceKeepsCompletedCompanyCompleted() {
    var registryCode = "12345678";
    var owner = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    kybScreeningService.screen(companyWith(owner));
    assertThat(onboardingRepository.findStatus(registryCode, LEGAL_ENTITY)).contains(COMPLETED);

    entityManager.flush();
    entityManager.clear();

    var ownerWithoutBeneficialOwnerEvidence =
        boardMemberOwner(PERSONAL_CODE, 100.0).beneficialOwner(false).build();
    var results = kybScreeningService.screen(companyWith(ownerWithoutBeneficialOwnerEvidence));

    assertThat(results)
        .anyMatch(check -> check.type() == KybCheckType.SOLE_MEMBER_OWNERSHIP && !check.success());
    assertThat(onboardingRepository.findStatus(registryCode, LEGAL_ENTITY)).contains(COMPLETED);
  }

  @Test
  void ownerChangeStillRejectsCompletedCompany() {
    var registryCode = "12345678";
    var owner = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    kybScreeningService.screen(companyWith(owner));

    entityManager.flush();
    entityManager.clear();

    var newOwnerWithoutBeneficialOwnerEvidence =
        boardMemberOwner("49001010001", 100.0).beneficialOwner(false).build();
    kybScreeningService.screen(companyWith(newOwnerWithoutBeneficialOwnerEvidence));

    assertThat(onboardingRepository.findStatus(registryCode, LEGAL_ENTITY)).contains(REJECTED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void rescreeningAnUnchangedDualOwnerCompanyDoesNotFlagDataChanged() {
    var person1 = boardMemberOwner(PERSONAL_CODE, 50.0).build();
    var person2 = boardMemberOwner("49001010001", 50.0).build();
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true),
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            LocalDate.now(clock).minusYears(3),
            List.of());

    kybScreeningService.screen(data);
    // Force a fresh read so the second screening deserializes metadata from JSON, exactly as a
    // separate production screening transaction does — not the in-transaction L1 cache.
    entityManager.flush();
    entityManager.clear();
    var secondResults = kybScreeningService.screen(data);

    var dataChanged =
        secondResults.stream()
            .filter(c -> c.type() == KybCheckType.DATA_CHANGED)
            .findFirst()
            .orElseThrow();
    assertThat(dataChanged.success()).isTrue();
    assertThat((List<Map<String, Object>>) dataChanged.metadata().get("changes")).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void rescreeningAfterAGenuineOwnerChangeStillFlagsDataChanged() {
    var firstOwner = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    kybScreeningService.screen(companyWith(firstOwner));
    entityManager.flush();
    entityManager.clear();

    // Genuine change: a different natural person now solely owns the company.
    var newOwner = boardMemberOwner("49001010001", 100.0).build();
    var secondResults = kybScreeningService.screen(companyWith(newOwner));

    var dataChanged =
        secondResults.stream()
            .filter(c -> c.type() == KybCheckType.DATA_CHANGED)
            .findFirst()
            .orElseThrow();
    assertThat(dataChanged.success()).isFalse();
    var changes = (List<Map<String, Object>>) dataChanged.metadata().get("changes");
    assertThat(changes).anyMatch(c -> "SOLE_MEMBER_OWNERSHIP".equals(c.get("check")));
  }

  @Test
  void companyFoundedLessThanAYearAgoCreatesFailingCompanyAgeCheck() {
    var person = boardMemberOwner(PERSONAL_CODE, 100.0).build();
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person),
            new SelfCertification(true, true, true),
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            LocalDate.now(clock).minusMonths(1),
            List.of());

    kybScreeningService.screen(data);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var ageCheck = amlChecks.stream().filter(c -> c.getType() == KYB_COMPANY_AGE).findFirst();
    assertThat(ageCheck).as("KYB_COMPANY_AGE AmlCheck should be persisted").isPresent();
    assertThat(ageCheck.get().isSuccess()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void rescreeningACompanyIgnoresAnotherCompanyOfTheSameRepresentative() {
    // AML #78: KybDataChangeDetector read history by personal_code only, so a representative tied
    // to
    // two companies had each company's checks compared against the other company's most recent row.
    // Identical nightly re-screenings then flagged DATA_CHANGED for every such company. History is
    // now scoped to the company, so another company of the same representative is ignored. The
    // clock
    // is advanced between screenings so the other company's row is unambiguously the most recent.
    var owner = boardMemberOwner(PERSONAL_CODE, 100.0).build();

    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    kybScreeningService.screen(companyFor("11111111", "62011", owner));
    entityManager.flush();
    entityManager.clear();

    ClockHolder.setClock(Clock.fixed(NOW.plus(1, DAYS), ZoneOffset.UTC));
    kybScreeningService.screen(companyFor("22222222", "41201", owner));
    entityManager.flush();
    entityManager.clear();

    ClockHolder.setClock(Clock.fixed(NOW.plus(2, DAYS), ZoneOffset.UTC));
    var rescreenFirstCompany = kybScreeningService.screen(companyFor("11111111", "62011", owner));

    var dataChanged =
        rescreenFirstCompany.stream()
            .filter(c -> c.type() == KybCheckType.DATA_CHANGED)
            .findFirst()
            .orElseThrow();
    assertThat(dataChanged.success()).isTrue();
    assertThat((List<Map<String, Object>>) dataChanged.metadata().get("changes")).isEmpty();
  }

  private KybCompanyData companyFor(String registryCode, String naceCode, KybRelatedPerson owner) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode(registryCode), "Test OÜ", naceCode, LegalForm.OÜ),
        PERSONAL_CODE,
        R,
        List.of(owner),
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null,
        List.of());
  }
}
