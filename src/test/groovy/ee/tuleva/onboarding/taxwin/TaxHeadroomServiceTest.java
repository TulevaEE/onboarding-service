package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.CONSENT_REFERENCE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.approved;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.refreshed;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.signed;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentService.leaseUntil;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.INVALID;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.conversion.ThirdPillarContributionsThisYear;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxHeadroomServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  private static final Long ID = 7L;
  private static final IncomeWindow WINDOW =
      new IncomeWindow(YearMonth.of(2025, 11), YearMonth.of(2026, 10));
  private static final Map<YearMonth, DeclaredMonth> DECLARED =
      Map.of(
          YearMonth.of(2026, 10),
          new DeclaredMonth(new BigDecimal("2500.00"), new BigDecimal("90.00")));

  @Mock private TaxDataConsentRepository consentRepository;
  @Mock private TaxIncomeStore incomeStore;
  @Mock private TaxCertificateClient taxCertificateClient;
  @Mock private ConsentServiceClient consentServiceClient;
  @Mock private ThirdPillarContributionsThisYear contributionsThisYear;

  private TaxHeadroomService service;
  private final AuthenticatedPerson person =
      authenticatedPersonFromUser(sampleUser().build()).build();
  private final Long userId = person.getUserIdOrThrow();

  @BeforeEach
  void setUp() {
    service =
        new TaxHeadroomService(
            consentRepository,
            incomeStore,
            taxCertificateClient,
            consentServiceClient,
            contributionsThisYear,
            Clock.fixed(NOW, ZoneId.of("Europe/Tallinn")));
  }

  @Test
  void aPersonWithoutAnApprovedConsentHasNoHeadroom() {
    given(consentRepository.findByUserId(userId)).willReturn(Optional.of(signed(ID, userId)));

    assertThat(service.headroom(person)).isEmpty();
    then(consentServiceClient).shouldHaveNoInteractions();
  }

  @Test
  void dataRefreshedWithinADayIsUsedAsItIs() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(23, HOURS))));
    givenStoredIncome();

    assertThat(service.headroom(person))
        .get()
        .extracting(TaxHeadroomResponse::incomeKnownThrough)
        .isEqualTo(YearMonth.of(2026, 10));
    then(taxCertificateClient).shouldHaveNoInteractions();
  }

  @Test
  void dataFromLastMonthIsRefreshedEvenWithinADay() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, Instant.parse("2026-10-31T21:00:00Z"))));
    givenRefreshSucceeds();
    givenStoredIncome();

    assertThat(service.headroom(person)).isPresent();
    then(incomeStore).should().replaceDeclared(userId, DECLARED);
  }

  @Test
  void refreshesTheIncomeOnceADay() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(25, HOURS))));
    givenRefreshSucceeds();
    givenStoredIncome();

    assertThat(service.headroom(person)).isPresent();
    then(incomeStore).should().replaceDeclared(userId, DECLARED);
  }

  @Test
  void showsTheStoredIncomeWhileAnotherRequestIsRefreshingIt() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(25, HOURS))));
    given(consentRepository.claimLease(any(), any(), any())).willReturn(0);
    givenStoredIncome();

    assertThat(service.headroom(person)).isPresent();
    then(consentServiceClient).shouldHaveNoInteractions();
  }

  @Test
  void showsIncomeRefreshedWithinAWeekWhenTheConsentServiceIsDown() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(6, DAYS))));
    given(consentRepository.claimLease(any(), any(), any())).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willThrow(new ConsentServiceUnavailableException("down"));
    givenStoredIncome();

    assertThat(service.headroom(person)).isPresent();
    then(consentRepository).should().releaseLease(ID, leaseUntil(NOW));
  }

  @Test
  void saysSoWhenTheTaxDataServiceIsDownAndTheStoredIncomeIsOlderThanAWeek() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(8, DAYS))));
    given(consentRepository.claimLease(any(), any(), any())).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.of(CONSENT_REFERENCE));
    given(taxCertificateClient.declaredIncome(person.getPersonalCode(), CONSENT_REFERENCE, WINDOW))
        .willThrow(new TaxDataUnavailableException("down", new RuntimeException()));

    assertThatThrownBy(() -> service.headroom(person))
        .isInstanceOf(TaxHeadroomUnavailableException.class);
  }

  @Test
  void saysSoWhenTheIncomeWasNeverFetchedAndTheConsentServiceIsDown() {
    given(consentRepository.findByUserId(userId)).willReturn(Optional.of(approved(ID, userId)));
    given(consentRepository.claimLease(any(), any(), any())).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willThrow(new ConsentServiceUnavailableException("down"));

    assertThatThrownBy(() -> service.headroom(person))
        .isInstanceOf(TaxHeadroomUnavailableException.class);
  }

  @Test
  void aConsentTheTaxDataServiceRejectsIsInvalidatedAndTheIncomeDeleted() {
    var consent = refreshed(ID, userId, NOW.minus(25, HOURS));
    given(consentRepository.findByUserId(userId)).willReturn(Optional.of(consent));
    given(consentRepository.claimLease(any(), any(), any())).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.of(CONSENT_REFERENCE));
    given(taxCertificateClient.declaredIncome(person.getPersonalCode(), CONSENT_REFERENCE, WINDOW))
        .willThrow(new InvalidTaxDataConsentException("expired", new RuntimeException()));
    given(consentRepository.findById(ID)).willReturn(Optional.of(consent));

    assertThat(service.headroom(person)).isEmpty();
    then(incomeStore).should().deleteAll(userId);
    assertThat(consent.getStatus()).isEqualTo(INVALID);
  }

  @Test
  void correctsTheIncomeOfAMonthNotDeclaredYet() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(1, HOURS))));
    givenStoredIncome();

    assertThat(
            service.correctExpectedIncome(
                person, YearMonth.of(2026, 12), new BigDecimal("3000.50")))
        .isPresent();
    then(incomeStore).should().correct(userId, YearMonth.of(2026, 12), new BigDecimal("3000.50"));
  }

  @Test
  void aDeclaredMonthCannotBeCorrected() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(1, HOURS))));
    given(incomeStore.declared(userId)).willReturn(DECLARED);

    assertThatThrownBy(
            () -> service.correctExpectedIncome(person, YearMonth.of(2026, 10), BigDecimal.TEN))
        .isInstanceOf(InvalidExpectedIncomeException.class);
  }

  @Test
  void aMonthOfAnotherYearCannotBeCorrected() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(1, HOURS))));

    assertThatThrownBy(
            () -> service.correctExpectedIncome(person, YearMonth.of(2027, 1), BigDecimal.TEN))
        .isInstanceOf(InvalidExpectedIncomeException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "100000.01", "10.005"})
  void anImpossibleMonthlyIncomeIsRefused(String amount) {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(1, HOURS))));
    given(incomeStore.declared(userId)).willReturn(DECLARED);

    assertThatThrownBy(
            () ->
                service.correctExpectedIncome(
                    person, YearMonth.of(2026, 12), new BigDecimal(amount)))
        .isInstanceOf(InvalidExpectedIncomeException.class);
    then(incomeStore).should(never()).correct(any(), any(), any());
  }

  @Test
  void expectedIncomeNeedsAnApprovedConsent() {
    given(consentRepository.findByUserId(userId)).willReturn(Optional.empty());

    assertThat(service.correctExpectedIncome(person, YearMonth.of(2026, 12), BigDecimal.TEN))
        .isEmpty();
    then(incomeStore).shouldHaveNoInteractions();
  }

  @Test
  void spreadsTheYearlyIncomeOverTheMonthsNotDeclaredYet() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(1, HOURS))));
    givenStoredIncome();

    assertThat(service.expectYearlyIncome(person, new BigDecimal("10500"))).isPresent();
    then(incomeStore)
        .should()
        .expectYearlyIncome(
            userId,
            new YearlyIncome(2026, new BigDecimal("10500")),
            Map.of(
                YearMonth.of(2026, 11), new BigDecimal("4000.00"),
                YearMonth.of(2026, 12), new BigDecimal("4000.00")));
  }

  @Test
  void spreadsTheYearlyIncomeAgainOverTheMonthsStillNotDeclaredAfterARefresh() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(25, HOURS))));
    givenRefreshSucceeds();
    givenStoredIncome();
    given(incomeStore.yearlyIncome(userId))
        .willReturn(Optional.of(new YearlyIncome(2026, new BigDecimal("10500.00"))));

    assertThat(service.headroom(person)).isPresent();
    var inOrder = inOrder(incomeStore);
    then(incomeStore).should(inOrder).replaceDeclared(userId, DECLARED);
    then(incomeStore)
        .should(inOrder)
        .expectYearlyIncome(
            userId,
            new YearlyIncome(2026, new BigDecimal("10500.00")),
            Map.of(
                YearMonth.of(2026, 11), new BigDecimal("4000.00"),
                YearMonth.of(2026, 12), new BigDecimal("4000.00")));
  }

  @Test
  void forgetsAYearlyIncomeTheDeclaredIncomeHasAlreadyPassed() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(25, HOURS))));
    givenRefreshSucceeds();
    givenStoredIncome();
    given(incomeStore.yearlyIncome(userId))
        .willReturn(Optional.of(new YearlyIncome(2026, new BigDecimal("2000.00"))));

    assertThat(service.headroom(person)).isPresent();
    then(incomeStore).should().forgetYearlyIncome(userId);
    then(incomeStore).should(never()).expectYearlyIncome(any(), any(), any());
  }

  @Test
  void forgetsTheYearlyIncomeOfAnEarlierYear() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(25, HOURS))));
    givenRefreshSucceeds();
    givenStoredIncome();
    given(incomeStore.yearlyIncome(userId))
        .willReturn(Optional.of(new YearlyIncome(2025, new BigDecimal("30000.00"))));

    assertThat(service.headroom(person)).isPresent();
    then(incomeStore).should().forgetYearlyIncome(userId);
    then(incomeStore).should(never()).expectYearlyIncome(any(), any(), any());
  }

  @Test
  void refreshesTheDeclaredIncomeBeforeSpreadingTheYearlyIncome() {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(25, HOURS))));
    givenRefreshSucceeds();
    givenStoredIncome();

    assertThat(service.expectYearlyIncome(person, new BigDecimal("10500"))).isPresent();
    var inOrder = inOrder(incomeStore);
    then(incomeStore).should(inOrder).replaceDeclared(userId, DECLARED);
    then(incomeStore).should(inOrder).expectYearlyIncome(eq(userId), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"2499.99", "10500.005", "202500.01"})
  void aYearlyIncomeBelowTheDeclaredIncomeOrBeyondTheMonthlyLimitIsRefused(String yearlyIncome) {
    given(consentRepository.findByUserId(userId))
        .willReturn(Optional.of(refreshed(ID, userId, NOW.minus(1, HOURS))));
    given(incomeStore.declared(userId)).willReturn(DECLARED);
    given(incomeStore.corrections(userId)).willReturn(Map.of());

    assertThatThrownBy(() -> service.expectYearlyIncome(person, new BigDecimal(yearlyIncome)))
        .isInstanceOf(InvalidExpectedIncomeException.class);
    then(incomeStore).should(never()).expectYearlyIncome(any(), any(), any());
  }

  @Test
  void yearlyIncomeNeedsAnApprovedConsent() {
    given(consentRepository.findByUserId(userId)).willReturn(Optional.empty());

    assertThat(service.expectYearlyIncome(person, BigDecimal.TEN)).isEmpty();
    then(incomeStore).shouldHaveNoInteractions();
  }

  @Test
  void aRepresentativeSeesNoTaxDataOfThePersonTheyRepresent() {
    var representative =
        authenticatedPersonFromUser(sampleUser().build())
            .role(new Role(RoleType.LEGAL_ENTITY, "12345678", "Company OÜ"))
            .build();

    assertThatThrownBy(() -> service.headroom(representative))
        .isInstanceOf(NotActingAsSelfException.class);
  }

  private void givenRefreshSucceeds() {
    given(consentRepository.claimLease(any(), any(), any())).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.of(CONSENT_REFERENCE));
    given(taxCertificateClient.declaredIncome(person.getPersonalCode(), CONSENT_REFERENCE, WINDOW))
        .willReturn(DECLARED);
    given(consentRepository.findById(ID)).willReturn(Optional.of(approved(ID, userId)));
    given(consentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  private void givenStoredIncome() {
    given(incomeStore.declared(userId)).willReturn(DECLARED);
    given(incomeStore.corrections(userId)).willReturn(Map.of());
    given(contributionsThisYear.forPerson(person)).willReturn(BigDecimal.ZERO);
  }
}
