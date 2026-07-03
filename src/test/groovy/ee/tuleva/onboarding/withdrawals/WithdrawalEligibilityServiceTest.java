package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalEligibilityServiceTest {

  Clock clock = Clock.fixed(Instant.parse("2024-10-10T10:00:00Z"), UTC);

  Clock estonianClock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneId.of("Europe/Tallinn");
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return ClockHolder.clock().withZone(zone);
        }

        @Override
        public Instant instant() {
          return ClockHolder.clock().instant();
        }
      };

  @Mock private EpisService episService;

  private WithdrawalEligibilityService withdrawalEligibilityService;

  @BeforeEach
  void setup() {
    ClockHolder.setClock(clock);
    withdrawalEligibilityService = new WithdrawalEligibilityService(episService, estonianClock);
  }

  @AfterEach
  void cleanup() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those over 60")
  void shouldCalculateWithdrawalEligibilityForThoseOver60() {
    var aPerson = sampleRetirementAgePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(30));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
    assertThat(result.canWithdrawThirdPillarWithReducedTax()).isTrue();
    assertThat(result.recommendedDurationYears()).isEqualTo(30);
    assertThat(result.age()).isEqualTo(60);
    assertThat(result.arrestsOrBankruptciesPresent()).isFalse();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isEqualTo(35);
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60 with arrests")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60WithArrest() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(true, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isEqualTo(35);
    assertThat(result.arrestsOrBankruptciesPresent()).isTrue();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60 with bankruptcies")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60WithBankruptcy() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, true));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isEqualTo(35);
    assertThat(result.arrestsOrBankruptciesPresent()).isTrue();
  }

  @Nested
  @DisplayName("third pillar early withdrawal")
  class ThirdPillarEarlyWithdrawal {

    @AfterEach
    void cleanup() {
      ClockHolder.setDefaultClock();
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for 5 years and III pillar opened before 2021")
    void shouldCalculateWithdrawalEligibilityWithoutThirdPillar() {
      Clock clock2028 = Clock.fixed(Instant.parse("2028-04-01T10:00:00Z"), UTC);
      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37212305258")
              .firstName("Jürto")
              .lastName("Nii-Dommzonn")
              .build();
      ClockHolder.setClock(clock2028);

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(null);

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isEqualTo(55);
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for 5 years and III pillar opened before 2021")
    void shouldCalculateWithdrawalEligibilityEarlyThirdPillar() {
      Clock clock2028 = Clock.fixed(Instant.parse("2028-04-01T10:00:00Z"), UTC);
      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37212305258")
              .firstName("Jürto")
              .lastName("Nii-Dommzonn")
              .build();
      ClockHolder.setClock(clock2028);

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTaxFrom())
          .isEqualTo(LocalDate.parse("2027-12-30"));
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isEqualTo(55);
    }

    @Test
    void reducedTaxAvailableFromAge55ForPre2021JoinersUnder55() {
      ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), UTC));
      var aPerson =
          PersonImpl.builder()
              .personalCode("39201155216")
              .firstName("Jaan")
              .lastName("Jõgi")
              .build();
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());

      given(episService.getContactDetails(aPerson)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(aPerson))
          .willReturn(new FundPensionCalculationDto(0));
      given(episService.getArrestsBankruptciesPresent(aPerson))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTaxFrom())
          .isEqualTo(LocalDate.parse("2047-01-15"));
      assertThat(result.earlyRetirementDate()).isEqualTo(LocalDate.parse("2052-04-15"));
      assertThat(result.age()).isEqualTo(34);
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for less than 5 years and III pillar opened before 2021")
    void shouldCalculateWithdrawalEligibilityEarlyThirdPillarLessThan5Years() {
      Clock clock2025 = Clock.fixed(Instant.parse("2025-04-01T10:00:00Z"), UTC);
      ClockHolder.setClock(clock2025);

      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37001275700")
              .firstName("Kert")
              .lastName("Ämmapets")
              .build();

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2020-12-01T00:00:00.000Z"));

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isEqualTo(55);
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for more than 5 years and III pillar opened after 2021")
    void shouldCalculateWithdrawalEligibilityEarlyThirdPillarAfter2021() {
      Clock clock2028 = Clock.fixed(Instant.parse("2028-04-01T10:00:00Z"), UTC);
      ClockHolder.setClock(clock2028);

      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37212305258")
              .firstName("Jürto")
              .lastName("Nii-Dommzonn")
              .build();

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-01-10T00:00:00.000Z"));

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isEqualTo(55);
    }
  }

  @Nested
  class ThirdPillarReducedTaxOverEarlyRetirementAge {

    final Clock clock2026 = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), UTC);

    final PersonImpl personOver60 =
        PersonImpl.builder().personalCode("46503035216").firstName("Miia").lastName("Mets").build();

    @AfterEach
    void cleanup() {
      ClockHolder.setDefaultClock();
    }

    @Test
    void noReducedTaxWhenThirdPillarOpenedAfter2021AndHeldUnderFiveYears() {
      ClockHolder.setClock(clock2026);
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(personOver60.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-10-15T00:00:00.000Z"));

      given(episService.getContactDetails(personOver60)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(personOver60))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(personOver60))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(personOver60);

      assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTaxFrom())
          .isEqualTo(LocalDate.parse("2026-10-15"));
      assertThat(result.earlyRetirementDate()).isEqualTo(LocalDate.parse("2025-03-03"));
      assertThat(result.age()).isEqualTo(61);
    }

    @Test
    void reducedTaxWhenThirdPillarOpenedAfter2021AndHeldOverFiveYears() {
      ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), UTC));
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(personOver60.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-10-15T00:00:00.000Z"));

      given(episService.getContactDetails(personOver60)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(personOver60))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(personOver60))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(personOver60);

      assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isTrue();
      assertThat(result.age()).isEqualTo(61);
    }

    @Test
    void eligibilityDateComparisonUsesEstonianCalendarDate() {
      ClockHolder.setClock(Clock.fixed(Instant.parse("2026-10-14T22:30:00Z"), UTC));
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(personOver60.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-10-15T00:00:00.000Z"));

      given(episService.getContactDetails(personOver60)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(personOver60))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(personOver60))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(personOver60);

      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTaxFrom())
          .isEqualTo(LocalDate.parse("2026-10-15"));
    }

    @Test
    void thirdPillarInitDateUsesEstonianCalendarDate() {
      ClockHolder.setClock(clock2026);
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(personOver60.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2020-12-31T22:30:00.000Z"));

      given(episService.getContactDetails(personOver60)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(personOver60))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(personOver60))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(personOver60);

      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTaxFrom())
          .isEqualTo(LocalDate.parse("2026-01-01"));
    }

    @Test
    void noReducedTaxWhenThirdPillarMissing() {
      ClockHolder.setClock(clock2026);
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(personOver60.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(null);

      given(episService.getContactDetails(personOver60)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(personOver60))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(personOver60))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(personOver60);

      assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTaxFrom()).isNull();
    }

    @Test
    void noReducedTaxWhenThirdPillarOpenedBefore2021ButHeldUnderFiveYears() {
      ClockHolder.setClock(Clock.fixed(Instant.parse("2024-10-10T10:00:00Z"), UTC));
      var aPerson = sampleRetirementAgePerson;
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2020-06-01T00:00:00.000Z"));

      given(episService.getContactDetails(aPerson)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(aPerson))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(aPerson))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.age()).isEqualTo(60);
    }

    @Test
    void notReachedEarlyRetirementAgeAt60Before2027ThresholdOf60Years1Month() {
      ClockHolder.setClock(Clock.fixed(Instant.parse("2027-01-20T10:00:00Z"), UTC));
      var aPerson =
          PersonImpl.builder()
              .personalCode("36701155216")
              .firstName("Mart")
              .lastName("Mets")
              .build();
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-10-15T00:00:00.000Z"));

      given(episService.getContactDetails(aPerson)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(aPerson))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(aPerson))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
      assertThat(result.age()).isEqualTo(60);
    }

    @Test
    void reachedEarlyRetirementAgeAt60Years1MonthIn2027() {
      ClockHolder.setClock(Clock.fixed(Instant.parse("2027-03-01T10:00:00Z"), UTC));
      var aPerson =
          PersonImpl.builder()
              .personalCode("36701155216")
              .firstName("Mart")
              .lastName("Mets")
              .build();
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-10-15T00:00:00.000Z"));

      given(episService.getContactDetails(aPerson)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(aPerson))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(aPerson))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isTrue();
      assertThat(result.age()).isEqualTo(60);
    }

    @Test
    void noReducedTaxWhenThirdPillarInactive() {
      ClockHolder.setClock(clock2026);
      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(personOver60.getPersonalCode());
      aContactDetails.setThirdPillarActive(false);

      given(episService.getContactDetails(personOver60)).willReturn(aContactDetails);
      given(episService.getFundPensionCalculation(personOver60))
          .willReturn(new FundPensionCalculationDto(20));
      given(episService.getArrestsBankruptciesPresent(personOver60))
          .willReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(personOver60);

      assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
      assertThat(result.canWithdrawThirdPillarWithReducedTax()).isFalse();
    }
  }
}
