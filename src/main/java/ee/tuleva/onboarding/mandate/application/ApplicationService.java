package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.exception.NotFoundException;
import ee.tuleva.onboarding.payment.application.PaymentLinkingService;
import ee.tuleva.onboarding.pillar.Pillar;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentDeadlinesService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentService;
import ee.tuleva.onboarding.savings.fund.application.SavingFundPaymentApplicationDetails;
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
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

  private final EpisService episService;
  private final LocaleService localeService;
  private final FundRepository fundRepository;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final PaymentLinkingService paymentLinkingService;
  private final SavingFundPaymentDeadlinesService savingFundPaymentDeadlinesService;
  private final SavingFundPaymentService savingFundPaymentService;

  public Application<?> getApplication(Long id, AuthenticatedPerson authenticatedPerson) {
    return getAllApplications(authenticatedPerson).stream()
        .filter(application -> application.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Application not found: id=" + id));
  }

  public List<Application<?>> getApplications(ApplicationStatus status, Person person) {
    return getAllApplications(person).stream().filter(byStatus(status)).collect(toList());
  }

  List<Application<?>> getAllApplications(Person person) {
    List<Application<?>> applications = new ArrayList<>();
    applications.addAll(getTransferApplications(person));
    applications.addAll(getWithdrawalApplications(person));
    applications.addAll(paymentLinkingService.getPaymentApplications(person));
    applications.addAll(getPaymentRateApplications(person));
    applications.addAll(getFundPensionOpeningApplications(person));
    applications.addAll(getSavingsFundPaymentApplications(person));
    Collections.sort(applications);
    return applications;
  }

  public boolean hasPendingWithdrawals(Person person, Pillar pillar) {
    return !getWithdrawalApplications(PENDING, person).stream()
        .filter(application -> application.getPillar() == pillar.toInt())
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
      Predicate<Entry<ApplicationType, List<ApplicationDTO>>> filterPredicate,
      Function<Entry<ApplicationType, List<ApplicationDTO>>, Stream<? extends Application<T>>>
          toApplicationMapper) {
    final var applicationsByType =
        episService.getApplications(person).stream().collect(groupingBy(ApplicationDTO::getType));
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

  @NotNull
  private Predicate<Application<?>> byStatus(ApplicationStatus status) {
    return application -> application.hasStatus(status);
  }

  private List<Application<SavingFundPaymentApplicationDetails>> getSavingsFundPaymentApplications(
      Person person) {

    // Savings fund payments are only available for authenticated users
    if (!(person instanceof AuthenticatedPerson authenticatedPerson)) {
      return List.of();
    }

    var payments = savingFundPaymentService.getPendingPaymentsForUser(authenticatedPerson);
    return payments.stream().map(this::convertSavingFundPayment).sorted().toList();
  }

  private List<Application<TransferApplicationDetails>> groupTransfers(
      List<ApplicationDTO> transferApplications) {
    Locale locale = localeService.getCurrentLocale();

    return transferApplications.stream()
        .map(
            applicationDto -> {
              final var deadlines = mandateDeadlinesService.getDeadlines(applicationDto.getDate());
              final var application = Application.<TransferApplicationDetails>builder();
              application.id(applicationDto.getId());
              application.creationTime(applicationDto.getDate());
              application.status(applicationDto.getStatus());
              final var sourceFund = fundRepository.findByIsin(applicationDto.getSourceFundIsin());
              final var details =
                  TransferApplicationDetails.builder()
                      .type(applicationDto.getType())
                      .sourceFund(new ApiFundResponse(sourceFund, locale))
                      .fulfillmentDate(deadlines.getFulfillmentDate(applicationDto.getType()))
                      .cancellationDeadline(
                          deadlines.getCancellationDeadline(applicationDto.getType()));
              applicationDto
                  .getFundTransferExchanges()
                  .forEach(
                      fundTransferExchange ->
                          details.exchange(
                              new Exchange(
                                  new ApiFundResponse(sourceFund, locale),
                                  getTargetFund(fundTransferExchange, locale),
                                  fundTransferExchange.getTargetPik(),
                                  fundTransferExchange.getAmount())));
              application.details(details.build());
              return application.build();
            })
        .collect(toList());
  }

  private ApiFundResponse getTargetFund(
      MandateFundsTransferExchangeDTO exchangeDTO, Locale locale) {
    String targetFundIsin = exchangeDTO.getTargetFundIsin();
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
      ApplicationDTO applicationDTO) {
    final var applicationBuilder =
        Application.<WithdrawalApplicationDetails>builder()
            .creationTime(applicationDTO.getDate())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());

    final var deadlines = mandateDeadlinesService.getDeadlines(applicationDTO.getDate());
    applicationBuilder.details(
        WithdrawalApplicationDetails.builder()
            .type(applicationDTO.getType())
            .depositAccountIBAN(applicationDTO.getBankAccount())
            .fulfillmentDate(deadlines.getFulfillmentDate(applicationDTO.getType()))
            .cancellationDeadline(deadlines.getCancellationDeadline(applicationDTO.getType()))
            .build());
    return applicationBuilder.build();
  }

  private Application<PaymentRateApplicationDetails> convertPaymentRate(
      ApplicationDTO applicationDTO) {
    final var applicationBuilder =
        Application.<PaymentRateApplicationDetails>builder()
            .creationTime(applicationDTO.getDate())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());

    final var deadlines = mandateDeadlinesService.getDeadlines(applicationDTO.getDate());
    applicationBuilder.details(
        PaymentRateApplicationDetails.builder()
            .type(applicationDTO.getType())
            .paymentRate(applicationDTO.getPaymentRate())
            .fulfillmentDate(deadlines.getFulfillmentDate(applicationDTO.getType()))
            .cancellationDeadline(deadlines.getCancellationDeadline(applicationDTO.getType()))
            .build());
    return applicationBuilder.build();
  }

  private Application<FundPensionOpeningApplicationDetails> convertFundPensionOpening(
      ApplicationDTO applicationDTO) {
    final var applicationBuilder =
        Application.<FundPensionOpeningApplicationDetails>builder()
            .creationTime(applicationDTO.getDate())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());

    final var deadlines = mandateDeadlinesService.getDeadlines(applicationDTO.getDate());
    applicationBuilder.details(
        new FundPensionOpeningApplicationDetails(
            applicationDTO.getBankAccount(),
            deadlines.getCancellationDeadline(applicationDTO.getType()),
            deadlines.getFulfillmentDate(applicationDTO.getType()),
            applicationDTO.getType(),
            applicationDTO.getFundPensionDetails()));
    return applicationBuilder.build();
  }

  private Application<SavingFundPaymentApplicationDetails> convertSavingFundPayment(
      SavingFundPayment payment) {
    final var applicationBuilder =
        Application.<SavingFundPaymentApplicationDetails>builder()
            .creationTime(payment.getCreatedAt())
            .status(PENDING) // TODO: Map real status when available
            // Only used for front-end uniqueness, otherwise meaningless
            .id(payment.getId().getMostSignificantBits());

    applicationBuilder.details(
        SavingFundPaymentApplicationDetails.builder()
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentId(payment.getId())
            .cancellationDeadline(
                savingFundPaymentDeadlinesService.getCancellationDeadline(payment))
            .fulfillmentDeadline(savingFundPaymentDeadlinesService.getFulfillmentDeadline(payment))
            .build());
    return applicationBuilder.build();
  }
}
