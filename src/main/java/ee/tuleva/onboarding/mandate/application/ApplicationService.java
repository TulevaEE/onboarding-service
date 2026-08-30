package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.company.BoardMembershipService;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.application.PaymentLinkingService;
import ee.tuleva.onboarding.pillar.Pillar;
import ee.tuleva.onboarding.savings.PendingRedemption;
import ee.tuleva.onboarding.savings.RedemptionQueries;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import ee.tuleva.onboarding.savings.fund.application.SavingFundPaymentApplicationDetails;
import ee.tuleva.onboarding.savings.fund.application.SavingFundWithdrawalApplicationDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

  private final MandateGateway mandateGateway;
  private final LocaleService localeService;
  private final FundRepository fundRepository;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final PaymentLinkingService paymentLinkingService;
  private final SavingFundDeadlinesService savingFundDeadlinesService;
  private final SavingFundPaymentQueries savingFundPaymentQueries;
  private final RedemptionQueries savingFundRedemptionQueries;
  private final BoardMembershipService boardMembershipService;

  public Application<?> getApplication(Long id, AuthenticatedPerson authenticatedPerson) {
    return getAllApplications(authenticatedPerson).stream()
        .filter(application -> application.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Application not found: id=" + id));
  }

  public List<Application<?>> getApplications(
      ApplicationStatus status, AuthenticatedPerson person) {
    return getAllApplications(person).stream().filter(byStatus(status)).collect(toList());
  }

  List<Application<?>> getAllApplications(AuthenticatedPerson person) {
    List<Application<?>> applications = new ArrayList<>();
    applications.addAll(getTransferApplications(person));
    applications.addAll(getWithdrawalApplications(person));
    applications.addAll(paymentLinkingService.getPaymentApplications(person));
    applications.addAll(getPaymentRateApplications(person));
    applications.addAll(getFundPensionOpeningApplications(person));
    applications.addAll(getSavingsFundApplications(person));
    Collections.sort(applications);
    return applications;
  }

  public boolean hasPendingWithdrawals(Person person, Pillar pillar) {
    return !getWithdrawalApplications(PENDING, person).stream()
        .filter(application -> Integer.valueOf(pillar.toInt()).equals(application.getPillar()))
        .toList()
        .isEmpty();
  }

  public List<Application<TransferApplicationDetails>> getTransferApplications(
      ApplicationStatus status, Person person) {
    return getTransferApplications(person).stream().filter(byStatus(status)).collect(toList());
  }

  public List<Application<PaymentRateApplicationDetails>> getPaymentRateApplications(
      Person person) {
    return getApplications(
        person,
        entry -> entry.getKey().isPaymentRate(),
        entry -> entry.getValue().stream().map(this::convertPaymentRate));
  }

  private List<Application<TransferApplicationDetails>> getTransferApplications(Person person) {
    return getApplications(
        person,
        entry -> entry.getKey().isTransfer(),
        entry -> groupTransfers(entry.getValue()).stream());
  }

  private List<Application<WithdrawalApplicationDetails>> getWithdrawalApplications(Person person) {
    return getApplications(
        person,
        entry -> entry.getKey().isWithdrawal(),
        entry -> entry.getValue().stream().map(this::convertWithdrawal));
  }

  private <T extends ApplicationDetails> List<Application<T>> getApplications(
      Person person,
      Predicate<Entry<ApplicationType, List<ApplicationSnapshot>>> filterPredicate,
      Function<Entry<ApplicationType, List<ApplicationSnapshot>>, Stream<? extends Application<T>>>
          toApplicationMapper) {
    final var applicationsByType =
        mandateGateway.getApplications(person).stream()
            .collect(groupingBy(ApplicationSnapshot::getType));
    return applicationsByType.entrySet().stream()
        .filter(filterPredicate)
        .flatMap(toApplicationMapper)
        .sorted()
        .collect(toList());
  }

  List<Application<WithdrawalApplicationDetails>> getWithdrawalApplications(
      ApplicationStatus status, Person person) {
    return getWithdrawalApplications(person).stream().filter(byStatus(status)).collect(toList());
  }

  private List<Application<FundPensionOpeningApplicationDetails>> getFundPensionOpeningApplications(
      Person person) {
    return getApplications(
        person,
        entry -> entry.getKey().isFundPensionOpening(),
        entry -> entry.getValue().stream().map(this::convertFundPensionOpening));
  }

  private Predicate<Application<?>> byStatus(ApplicationStatus status) {
    return application -> application.hasStatus(status);
  }

  private List<Application<? extends ApplicationDetails>> getSavingsFundApplications(
      AuthenticatedPerson person) {
    var activeParty = person.toPartyId();
    if (activeParty.type() == PartyId.Type.LEGAL_ENTITY
        && !boardMembershipService.isBoardMember(person.getPersonalCode(), activeParty.code())) {
      log.info(
          "Skipping savings-fund applications for stale legal-entity role: personalCode={}, registryCode={}",
          person.getPersonalCode(),
          activeParty.code());
      return List.of();
    }
    var payments = savingFundPaymentQueries.getPendingPayments(activeParty);
    var redemptionRequests = savingFundRedemptionQueries.getPendingRedemptions(activeParty);
    return Stream.concat(
            payments.stream().map(this::convertSavingFundPayment),
            redemptionRequests.stream().map(this::convertSavingFundRedemptionRequest))
        .toList();
  }

  private List<Application<TransferApplicationDetails>> groupTransfers(
      List<ApplicationSnapshot> transferApplications) {
    Locale locale = localeService.getCurrentLocale();

    return transferApplications.stream()
        .map(
            applicationSnapshot -> {
              final var deadlines =
                  mandateDeadlinesService.getDeadlines(applicationSnapshot.getDate());
              final var application = Application.<TransferApplicationDetails>builder();
              application.id(applicationSnapshot.getId());
              application.creationTime(applicationSnapshot.getDate());
              application.status(applicationSnapshot.getStatus());
              final var sourceFund =
                  fundRepository.findByIsin(
                      requireNonNull(
                          applicationSnapshot.getSourceFundIsin(),
                          "Source fund isin missing: applicationId="
                              + applicationSnapshot.getId()));
              final var details =
                  TransferApplicationDetails.builder()
                      .type(applicationSnapshot.getType())
                      .sourceFund(new ApiFundResponse(sourceFund, locale))
                      .fulfillmentDate(deadlines.getFulfillmentDate(applicationSnapshot.getType()))
                      .cancellationDeadline(
                          deadlines.getCancellationDeadline(applicationSnapshot.getType()));
              applicationSnapshot
                  .getFundTransferExchanges()
                  .forEach(
                      fundTransferExchange ->
                          details.exchange(
                              new Exchange(
                                  new ApiFundResponse(sourceFund, locale),
                                  getTargetFund(fundTransferExchange, locale),
                                  fundTransferExchange.targetPik(),
                                  fundTransferExchange.amount())));
              application.details(details.build());
              return application.build();
            })
        .collect(toList());
  }

  private @Nullable ApiFundResponse getTargetFund(
      ApplicationSnapshot.FundTransfer exchangeDTO, Locale locale) {
    String targetFundIsin = exchangeDTO.targetFundIsin();
    if (targetFundIsin == null) {
      return null;
    }

    Fund targetFund = fundRepository.findByIsin(targetFundIsin);
    if (targetFund == null) {
      throw new IllegalArgumentException(
          "Fund not found in the database: targetFundIsin=" + targetFundIsin);
    }
    return new ApiFundResponse(targetFund, locale);
  }

  private Application<WithdrawalApplicationDetails> convertWithdrawal(
      ApplicationSnapshot applicationSnapshot) {
    final var applicationBuilder =
        Application.<WithdrawalApplicationDetails>builder()
            .creationTime(applicationSnapshot.getDate())
            .status(applicationSnapshot.getStatus())
            .id(applicationSnapshot.getId());

    final var deadlines = mandateDeadlinesService.getDeadlines(applicationSnapshot.getDate());
    applicationBuilder.details(
        WithdrawalApplicationDetails.builder()
            .type(applicationSnapshot.getType())
            .depositAccountIBAN(
                requireNonNull(
                    applicationSnapshot.getBankAccount(),
                    "Bank account missing: applicationId=" + applicationSnapshot.getId()))
            .fulfillmentDate(deadlines.getFulfillmentDate(applicationSnapshot.getType()))
            .cancellationDeadline(deadlines.getCancellationDeadline(applicationSnapshot.getType()))
            .build());
    return applicationBuilder.build();
  }

  private Application<PaymentRateApplicationDetails> convertPaymentRate(
      ApplicationSnapshot applicationSnapshot) {
    final var applicationBuilder =
        Application.<PaymentRateApplicationDetails>builder()
            .creationTime(applicationSnapshot.getDate())
            .status(applicationSnapshot.getStatus())
            .id(applicationSnapshot.getId());

    final var deadlines = mandateDeadlinesService.getDeadlines(applicationSnapshot.getDate());
    applicationBuilder.details(
        PaymentRateApplicationDetails.builder()
            .type(applicationSnapshot.getType())
            .paymentRate(
                requireNonNull(
                    applicationSnapshot.getPaymentRate(),
                    "Payment rate missing: applicationId=" + applicationSnapshot.getId()))
            .fulfillmentDate(deadlines.getFulfillmentDate(applicationSnapshot.getType()))
            .cancellationDeadline(deadlines.getCancellationDeadline(applicationSnapshot.getType()))
            .build());
    return applicationBuilder.build();
  }

  private Application<FundPensionOpeningApplicationDetails> convertFundPensionOpening(
      ApplicationSnapshot applicationSnapshot) {
    final var applicationBuilder =
        Application.<FundPensionOpeningApplicationDetails>builder()
            .creationTime(applicationSnapshot.getDate())
            .status(applicationSnapshot.getStatus())
            .id(applicationSnapshot.getId());

    final var deadlines = mandateDeadlinesService.getDeadlines(applicationSnapshot.getDate());
    applicationBuilder.details(
        new FundPensionOpeningApplicationDetails(
            requireNonNull(
                applicationSnapshot.getBankAccount(),
                "Bank account missing: applicationId=" + applicationSnapshot.getId()),
            deadlines.getCancellationDeadline(applicationSnapshot.getType()),
            deadlines.getFulfillmentDate(applicationSnapshot.getType()),
            applicationSnapshot.getType(),
            requireNonNull(
                applicationSnapshot.getFundPensionDetails(),
                "Fund pension details missing: applicationId=" + applicationSnapshot.getId())));
    return applicationBuilder.build();
  }

  private Application<SavingFundPaymentApplicationDetails> convertSavingFundPayment(
      SavingFundPayment payment) {
    final var applicationBuilder =
        Application.<SavingFundPaymentApplicationDetails>builder()
            .creationTime(payment.getCreatedAt())
            .status(PENDING)
            // Only used for front-end uniqueness, otherwise meaningless
            .id(payment.getId().getMostSignificantBits());

    var cancellationDeadline =
        savingFundDeadlinesService.getCancellationDeadline(payment).minusSeconds(1);

    applicationBuilder.details(
        SavingFundPaymentApplicationDetails.builder()
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentId(payment.getId())
            .cancelledAt(payment.getCancelledAt())
            .cancellationDeadline(payment.getCancelledAt() != null ? null : cancellationDeadline)
            .fulfillmentDeadline(savingFundDeadlinesService.getFulfillmentDeadline(payment))
            .build());
    return applicationBuilder.build();
  }

  private Application<SavingFundWithdrawalApplicationDetails> convertSavingFundRedemptionRequest(
      PendingRedemption redemption) {
    return Application.<SavingFundWithdrawalApplicationDetails>builder()
        .creationTime(redemption.requestedAt())
        .status(PENDING)
        .id(redemption.id().getMostSignificantBits())
        .details(
            SavingFundWithdrawalApplicationDetails.builder()
                .id(redemption.id())
                .amount(redemption.amount())
                .currency(Currency.EUR)
                .iban(redemption.customerIban())
                .cancellationDeadline(redemption.cancellationDeadline().minusSeconds(1))
                .fulfillmentDeadline(redemption.fulfillmentDeadline())
                .build())
        .build();
  }
}
