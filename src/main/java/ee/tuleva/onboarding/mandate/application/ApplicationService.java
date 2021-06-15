package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.response.FundDto;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.application.Application.ApplicationBuilder;
import ee.tuleva.onboarding.mandate.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

  private final EpisService episService;
  private final LocaleService localeService;
  private final FundRepository fundRepository;
  private final MandateDeadlinesService mandateDeadlinesService;

  public Application getApplication(Long id, AuthenticatedPerson authenticatedPerson) {
    return getApplications(authenticatedPerson).stream()
        .filter(application -> application.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Application not found: id=" + id));
  }

  public boolean hasPendingWithdrawals(Person person) {
    return getApplications(person).stream()
        .anyMatch(application -> application.isPending() && application.isWithdrawal());
  }

  public List<Application> getApplications(ApplicationStatus status, Person person) {
    return getApplications(person).stream()
        .filter(application -> application.getStatus().equals(status))
        .collect(toList());
  }

  public List<Application> getApplications(Person person) {
    val applicationsByType =
        episService.getApplications(person).stream().collect(groupingBy(ApplicationDTO::getType));
    return applicationsByType.entrySet().stream()
        .flatMap(
            entry -> {
              if (entry.getKey() == TRANSFER) {
                return groupTransfers(entry.getValue()).stream();
              } else {
                return entry.getValue().stream().map(this::convert);
              }
            })
        .sorted()
        .collect(toList());
  }

  private List<TransferApplication> groupTransfers(List<ApplicationDTO> transferApplications) {
    val locale = localeService.getCurrentLocale();
    log.info("Grouping transfers {}", transferApplications);
    return transferApplications.stream()
        .map(
            applicationDto -> {
              val deadlines = mandateDeadlinesService.getDeadlines(applicationDto.getDate());
              val application = TransferApplication.builder();
              application.id(applicationDto.getId());
              application.creationTime(applicationDto.getDate());
              application.status(applicationDto.getStatus());
              application.type(applicationDto.getType());
              val sourceFund = fundRepository.findByIsin(applicationDto.getSourceFundIsin());
              val details =
                  TransferApplicationDetails.builder()
                      .sourceFund(new FundDto(sourceFund, locale.getLanguage()));
              details
                  .fulfillmentDate(deadlines.getFulfillmentDate(applicationDto.getType()))
                  .cancellationDeadline(
                      deadlines.getCancellationDeadline(applicationDto.getType()));
              applicationDto
                  .getFundTransferExchanges()
                  .forEach(
                      fundTransferExchange -> {
                        val targetFund =
                            fundRepository.findByIsin(fundTransferExchange.getTargetFundIsin());
                        details.exchange(
                            TransferApplicationDetails.Exchange.builder()
                                .amount(fundTransferExchange.getAmount())
                                .sourceFund(new FundDto(sourceFund, locale.getLanguage()))
                                .targetFund(new FundDto(targetFund, locale.getLanguage()))
                                .build());
                      });
              application.details(details.build());
              return application.build();
            })
        .collect(toList());
  }

  private Application convert(ApplicationDTO applicationDTO) {
    ApplicationBuilder<? extends Application, ?> applicationBuilder =
        Application.builder()
            .creationTime(applicationDTO.getDate())
            .type(applicationDTO.getType())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());
    if (applicationDTO.isWithdrawal()) {
      addWithdrawalInfo(applicationBuilder, applicationDTO);
    }
    return applicationBuilder.build();
  }

  private void addWithdrawalInfo(
      ApplicationBuilder<? extends Application, ?> applicationBuilder,
      ApplicationDTO applicationDto) {

    val deadlines = mandateDeadlinesService.getDeadlines(applicationDto.getDate());
    applicationBuilder.details(
        WithdrawalApplicationDetails.builder()
            .depositAccountIBAN(applicationDto.getBankAccount())
            .fulfillmentDate(deadlines.getFulfillmentDate(applicationDto.getType()))
            .cancellationDeadline(deadlines.getCancellationDeadline(applicationDto.getType()))
            .build());
  }
}
