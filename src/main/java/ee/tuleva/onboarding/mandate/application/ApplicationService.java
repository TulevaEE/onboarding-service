package ee.tuleva.onboarding.mandate.application;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.response.FundDto;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationService {

  private final EpisService episService;
  private final MandateCancellationService mandateCancellationService;
  private final LocaleService localeService;
  private final FundRepository fundRepository;

  public List<Application> get(Person person) {
    val applicationsByType =
        episService.getApplications(person).stream().collect(groupingBy(ApplicationDTO::getType));
    return applicationsByType.entrySet().stream()
        .flatMap(
            elem -> {
              if (elem.getKey() == ApplicationType.TRANSFER) {
                return groupTransfers(elem.getValue()).stream();
              } else {
                return elem.getValue().stream().map(this::convert);
              }
            })
        .sorted()
        .collect(toList());
  }

  private List<Application> groupTransfers(List<ApplicationDTO> transferApplications) {
    val locale = localeService.getCurrentLocale();
    val transfersBySource =
        transferApplications.stream().collect(groupingBy(ApplicationDTO::getSourceFundIsin));
    return transfersBySource.entrySet().stream()
        .map(
            transfers -> {
              val application = Application.builder();
              val firstTransfer =
                  transfers.getValue().stream().findFirst().orElseThrow(IllegalStateException::new);
              application.id(firstTransfer.getId());
              application.creationTime(firstTransfer.getDate());
              application.status(firstTransfer.getStatus());
              application.type(firstTransfer.getType());
              val sourceFund = fundRepository.findByIsin(transfers.getKey());
              val details =
                  TransferApplicationDetails.builder()
                      .sourceFund(new FundDto(sourceFund, locale.getLanguage()));
              transfers
                  .getValue()
                  .forEach(
                      applicationDTO -> {
                        val targetFund =
                            fundRepository.findByIsin(applicationDTO.getTargetFundIsin());
                        details.exchange(
                            TransferApplicationDetails.Exchange.builder()
                                .amount(applicationDTO.getAmount())
                                .targetFund(new FundDto(targetFund, locale.getLanguage()))
                                .build());
                      });
              application.details(details.build());
              return application.build();
            })
        .collect(toList());
  }

  private Application convert(ApplicationDTO applicationDTO) {
    val applicationBuilder =
        Application.builder()
            .creationTime(applicationDTO.getDate())
            .type(applicationDTO.getType())
            .status(applicationDTO.getStatus())
            .id(applicationDTO.getId());
    if (applicationDTO.getType().equals(WITHDRAWAL)
        || applicationDTO.getType().equals(EARLY_WITHDRAWAL)) {
      addWithdrawalInfo(applicationBuilder, applicationDTO);
    }
    return applicationBuilder.build();
  }

  private void addWithdrawalInfo(
      Application.ApplicationBuilder applicationBuilder, ApplicationDTO applicationDTO) {
    applicationBuilder.details(
        WithdrawalApplicationDetails.builder()
            .depositAccountIBAN(applicationDTO.getBankAccount())
            .build());
  }

  private ApplicationDTO getApplication(Long applicationId, Person person) {
    List<ApplicationDTO> applications = episService.getApplications(person);
    return applications.stream()
        .filter(application -> application.getId().equals(applicationId))
        .collect(onlyElement());
  }

  public ApplicationCancellationResponse createCancellationMandate(
      Person person, Long userId, Long applicationId) {
    ApplicationDTO applicationToCancel = getApplication(applicationId, person);
    Mandate mandate =
        mandateCancellationService.saveCancellationMandate(userId, applicationToCancel);
    return new ApplicationCancellationResponse(mandate.getId());
  }
}
