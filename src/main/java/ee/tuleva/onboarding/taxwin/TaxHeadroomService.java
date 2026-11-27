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

  private final TaxDataConsentRepository consentRepository;
  private final TaxIncomeStore incomeStore;
  private final IncomeExpectations incomeExpectations;
  private final TaxCertificateClient taxCertificateClient;
  private final ConsentServiceClient consentServiceClient;
  private final ThirdPillarContributionsThisYear contributionsThisYear;
  private final Clock estonianClock;
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
    incomeExpectations.expectMonthlyIncome(
        person.getUserIdOrThrow(), YearMonth.now(estonianClock), month, amount);
    return headroom(person);
  }

  Optional<TaxHeadroomResponse> expectYearlyIncome(
      AuthenticatedPerson person, BigDecimal yearlyIncome) {
    requireActingAsSelf(person);
    if (approvedConsent(person).flatMap(consent -> refreshed(consent, person)).isEmpty()) {
      return Optional.empty();
    }
    incomeExpectations.expectYearlyIncome(
        person.getUserIdOrThrow(), YearMonth.now(estonianClock), yearlyIncome);
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
      incomeExpectations.spreadYearlyIncomeAgain(userId, YearMonth.now(estonianClock));
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
    IncomeEstimate estimate = incomeExpectations.estimate(userId, currentMonth);
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
}
