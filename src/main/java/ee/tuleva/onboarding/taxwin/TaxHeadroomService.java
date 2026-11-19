package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.TaxDataConsentService.leaseUntil;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentService.requireActingAsSelf;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ThirdPillarContributionsThisYear;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Month;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class TaxHeadroomService {

  private static final Duration REFRESH_INTERVAL = Duration.ofHours(24);
  private static final Duration STALE_DATA_LIMIT = Duration.ofDays(7);
  private static final BigDecimal LARGEST_EXPECTED_MONTHLY_INCOME = new BigDecimal("100000");
  private static final int CENTS = 2;

  private final TaxDataConsentRepository consentRepository;
  private final TaxIncomeStore incomeStore;
  private final TaxCertificateClient taxCertificateClient;
  private final ConsentServiceClient consentServiceClient;
  private final ThirdPillarContributionsThisYear contributionsThisYear;
  private final Clock estonianClock;
  private final IncomeEstimator estimator = new IncomeEstimator();
  private final TaxHeadroomCalculator calculator = new TaxHeadroomCalculator();

  Optional<TaxHeadroomResponse> headroom(AuthenticatedPerson person) {
    requireActingAsSelf(person);
    return approvedConsent(person)
        .flatMap(consent -> refreshed(consent, person))
        .map(consent -> calculate(person));
  }

  Optional<TaxHeadroomResponse> correctExpectedIncome(
      AuthenticatedPerson person, YearMonth month, BigDecimal amount) {
    requireActingAsSelf(person);
    if (approvedConsent(person).isEmpty()) {
      return Optional.empty();
    }
    Long userId = person.getUserIdOrThrow();
    if (!isCorrectable(userId, month) || !isValidMonthlyIncome(amount)) {
      throw new InvalidExpectedIncomeException(month);
    }
    incomeStore.correct(userId, month, amount);
    return headroom(person);
  }

  Optional<TaxHeadroomResponse> expectYearlyIncome(
      AuthenticatedPerson person, BigDecimal yearlyIncome) {
    requireActingAsSelf(person);
    if (approvedConsent(person).flatMap(consent -> refreshed(consent, person)).isEmpty()) {
      return Optional.empty();
    }
    Long userId = person.getUserIdOrThrow();
    YearMonth currentMonth = YearMonth.now(estonianClock);
    Map<YearMonth, BigDecimal> expected =
        spread(userId, currentMonth, yearlyIncome)
            .orElseThrow(() -> new InvalidExpectedIncomeException(currentMonth.getYear()));
    incomeStore.expectYearlyIncome(
        userId, new YearlyIncome(currentMonth.getYear(), yearlyIncome), expected);
    return Optional.of(calculate(person));
  }

  Optional<TaxHeadroomResponse> removeCorrection(AuthenticatedPerson person, YearMonth month) {
    requireActingAsSelf(person);
    if (approvedConsent(person).isEmpty()) {
      return Optional.empty();
    }
    incomeStore.removeCorrection(person.getUserIdOrThrow(), month);
    return headroom(person);
  }

  private Optional<TaxDataConsent> approvedConsent(AuthenticatedPerson person) {
    return consentRepository
        .findByUserId(person.getUserIdOrThrow())
        .filter(consent -> consent.is(APPROVED));
  }

  private Optional<TaxDataConsent> refreshed(TaxDataConsent consent, AuthenticatedPerson person) {
    Instant now = estonianClock.instant();
    if (!needsRefresh(consent, now)) {
      return Optional.of(consent);
    }
    Long id = consent.getIdOrThrow();
    Long userId = person.getUserIdOrThrow();
    Instant leaseUntil = leaseUntil(now);
    if (consentRepository.claimLease(id, now, leaseUntil) == 0) {
      return servable(consent, userId, now);
    }
    try {
      Optional<String> reference = consentServiceClient.findReference(person.getPersonalCode());
      if (reference.isEmpty()) {
        return invalidated(id, userId);
      }
      incomeStore.replaceDeclared(
          userId,
          taxCertificateClient.declaredIncome(
              person.getPersonalCode(),
              reference.get(),
              IncomeWindow.endingBefore(YearMonth.now(estonianClock))));
      spreadYearlyIncomeAgain(userId);
      TaxDataConsent refreshed = consentRepository.findById(id).orElseThrow();
      refreshed.approve(reference.get());
      refreshed.refreshed(now);
      return Optional.of(consentRepository.save(refreshed));
    } catch (InvalidTaxDataConsentException e) {
      log.warn("Tax data consent is no longer valid: userId={}", userId, e);
      return invalidated(id, userId);
    } catch (ConsentServiceUnavailableException | TaxDataUnavailableException e) {
      log.warn("Tax data could not be refreshed: userId={}", userId, e);
      consentRepository.releaseLease(id, leaseUntil);
      return servable(consent, userId, now);
    } catch (ConsentRejectedException | TaxDataRequestRejectedException e) {
      log.error("Tax data request was rejected: userId={}", userId, e);
      consentRepository.releaseLease(id, leaseUntil);
      return servable(consent, userId, now);
    } catch (RuntimeException e) {
      consentRepository.releaseLease(id, leaseUntil);
      throw e;
    }
  }

  private void spreadYearlyIncomeAgain(Long userId) {
    YearMonth currentMonth = YearMonth.now(estonianClock);
    incomeStore
        .yearlyIncome(userId)
        .ifPresent(
            yearlyIncome ->
                Optional.of(yearlyIncome)
                    .filter(income -> income.isFor(currentMonth.getYear()))
                    .flatMap(income -> spread(userId, currentMonth, income.amount()))
                    .ifPresentOrElse(
                        expected -> incomeStore.expectYearlyIncome(userId, yearlyIncome, expected),
                        () -> incomeStore.forgetYearlyIncome(userId)));
  }

  private Optional<Map<YearMonth, BigDecimal>> spread(
      Long userId, YearMonth currentMonth, BigDecimal yearlyIncome) {
    Map<YearMonth, BigDecimal> expected =
        estimate(userId, currentMonth).spreadOverExpectedMonths(yearlyIncome);
    return expected.values().stream().allMatch(TaxHeadroomService::isValidMonthlyIncome)
        ? Optional.of(expected)
        : Optional.empty();
  }

  private boolean needsRefresh(TaxDataConsent consent, Instant now) {
    Instant refreshedAt = consent.getRefreshedAt();
    return refreshedAt == null
        || refreshedAt.plus(REFRESH_INTERVAL).isBefore(now)
        || !YearMonth.from(refreshedAt.atZone(estonianClock.getZone()))
            .equals(YearMonth.now(estonianClock));
  }

  private Optional<TaxDataConsent> servable(TaxDataConsent consent, Long userId, Instant now) {
    Instant refreshedAt = consent.getRefreshedAt();
    if (refreshedAt == null || refreshedAt.plus(STALE_DATA_LIMIT).isBefore(now)) {
      throw new TaxHeadroomUnavailableException(userId);
    }
    return Optional.of(consent);
  }

  private Optional<TaxDataConsent> invalidated(Long id, Long userId) {
    incomeStore.deleteAll(userId);
    TaxDataConsent consent = consentRepository.findById(id).orElseThrow();
    consent.invalidate();
    consentRepository.save(consent);
    return Optional.empty();
  }

  private TaxHeadroomResponse calculate(AuthenticatedPerson person) {
    Long userId = person.getUserIdOrThrow();
    YearMonth currentMonth = YearMonth.now(estonianClock);
    IncomeEstimate estimate = estimate(userId, currentMonth);
    BigDecimal paid = contributionsThisYear.forPerson(person);
    int monthsLeft = Month.DECEMBER.getValue() - currentMonth.getMonthValue() + 1;
    HeadroomFigures figures =
        calculator.calculate(
            estimate.knownIncome(),
            estimate.knownContributions(),
            estimate.expectedTotal(),
            paid,
            monthsLeft);
    return TaxHeadroomResponse.of(currentMonth.getYear(), estimate, paid, monthsLeft, figures);
  }

  private IncomeEstimate estimate(Long userId, YearMonth currentMonth) {
    return estimator.estimate(
        currentMonth, incomeStore.declared(userId), incomeStore.corrections(userId));
  }

  private boolean isCorrectable(Long userId, YearMonth month) {
    return month.getYear() == YearMonth.now(estonianClock).getYear()
        && !incomeStore.declared(userId).containsKey(month);
  }

  private static boolean isValidMonthlyIncome(BigDecimal amount) {
    return amount.signum() >= 0
        && amount.compareTo(LARGEST_EXPECTED_MONTHLY_INCOME) <= 0
        && amount.stripTrailingZeros().scale() <= CENTS;
  }
}
