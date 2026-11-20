package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.SIGNED;
import static java.time.temporal.ChronoUnit.MILLIS;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class TaxDataConsentService {

  private static final Duration LEASE_LONGER_THAN_THE_SLOWEST_REMOTE_CALLS = Duration.ofMinutes(2);

  private final TaxDataConsentRepository repository;
  private final ConsentServiceClient consentServiceClient;
  private final TaxIncomeStore incomeStore;
  private final Clock estonianClock;

  TaxDataConsentResponse create(AuthenticatedPerson person, Locale locale) {
    requireActingAsSelf(person);
    Long userId = person.getUserIdOrThrow();
    Optional<TaxDataConsent> existing = repository.findByUserId(userId);
    if (existing.filter(it -> it.isStillUsableAt(estonianClock.instant())).isPresent()) {
      return TaxDataConsentResponse.from(existing.get());
    }
    TaxDataConsent consent =
        existing.orElseGet(() -> new TaxDataConsent(userId, estonianClock.instant()));
    switch (consentServiceClient.requestConsent(person.getPersonalCode(), locale)) {
      case ConsentDocument document -> consent.awaitSignatureOf(document, estonianClock.instant());
      case ConsentAlreadyApproved() -> consent.approve(referenceOf(person));
    }
    return TaxDataConsentResponse.from(repository.save(consent));
  }

  TaxDataConsent get(Long id, AuthenticatedPerson person) {
    requireActingAsSelf(person);
    return repository
        .findByIdAndUserId(id, person.getUserIdOrThrow())
        .orElseThrow(() -> new NotFoundException("Tax data consent not found: id=" + id));
  }

  byte[] document(Long id, AuthenticatedPerson person) {
    byte[] document = get(id, person).getConsentDocument();
    if (document == null) {
      throw new NotFoundException("Tax data consent has no document to sign: id=" + id);
    }
    return document;
  }

  void markSigned(Long id, AuthenticatedPerson person, byte[] container) {
    TaxDataConsent consent = get(id, person);
    consent.sign(container);
    repository.save(consent);
  }

  TaxDataConsentStatus completeApproval(Long id, AuthenticatedPerson person) {
    TaxDataConsent consent = get(id, person);
    if (!consent.is(SIGNED)) {
      return consent.getStatus();
    }
    Instant now = estonianClock.instant();
    Instant leaseUntil = leaseUntil(now);
    if (repository.claimLease(id, now, leaseUntil) == 0) {
      return SIGNED;
    }
    try {
      Optional<String> reference = consentServiceClient.findReference(person.getPersonalCode());
      if (reference.isEmpty()) {
        consentServiceClient.approve(
            consent.confirmReferenceOrThrow(), consent.signedContainerOrThrow());
      }
      String approvedReference = reference.orElseGet(() -> referenceOf(person));
      return save(get(id, person), approved -> approved.approve(approvedReference));
    } catch (ConsentRejectedException e) {
      log.warn("Tax data consent was rejected: id={}", id, e);
      incomeStore.deleteAll(person.getUserIdOrThrow());
      return save(get(id, person), TaxDataConsent::invalidate);
    } catch (ConsentServiceUnavailableException e) {
      log.warn("Tax data consent approval is pending: id={}", id, e);
      repository.releaseLease(id, leaseUntil);
      return SIGNED;
    } catch (RuntimeException e) {
      repository.releaseLease(id, leaseUntil);
      throw e;
    }
  }

  private TaxDataConsentStatus save(TaxDataConsent consent, Consumer<TaxDataConsent> change) {
    change.accept(consent);
    return repository.save(consent).getStatus();
  }

  private String referenceOf(AuthenticatedPerson person) {
    return consentServiceClient
        .findReference(person.getPersonalCode())
        .orElseThrow(
            () ->
                new ConsentServiceUnavailableException(
                    "Consent service has no reference for an approved consent yet"));
  }

  static Instant leaseUntil(Instant now) {
    return now.plus(LEASE_LONGER_THAN_THE_SLOWEST_REMOTE_CALLS).truncatedTo(MILLIS);
  }

  static void requireActingAsSelf(AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      throw new NotActingAsSelfException();
    }
  }
}
