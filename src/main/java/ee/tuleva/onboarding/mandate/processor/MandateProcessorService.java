package ee.tuleva.onboarding.mandate.processor;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand;
import ee.tuleva.onboarding.epis.mandate.details.CancellationMandateDetails;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.payment.rate.PaymentRateDto;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorService {

  private final MandateProcessRepository mandateProcessRepository;
  private final MandateProcessErrorResolver mandateProcessErrorResolver;
  private final EpisService episService;

  public void start(User user, Mandate mandate) {
    log.info(
        "Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());

    if (mandate.isWithdrawalCancellation()) {
      final var response = episService.sendMandateV2(getMandateCommand(getCancellationDto(mandate)));
      handleApplicationProcessResponse(new ApplicationResponseDTO(response));
    } else if (mandate.isPaymentRateApplication()) {
      final var response = episService.sendPaymentRateApplication(getPaymentRateDto(mandate));
      handleApplicationProcessResponse(new ApplicationResponseDTO(response));
    } else {
      final var response = episService.sendMandate(getMandateDto(mandate));
      handleApplicationProcessResponse(response);
    }
  }

  private MandateDto getMandateDto(Mandate mandate) {
    final var mandateDtoBuilder =
        MandateDto.builder()
            .id(mandate.getId())
            .createdDate(mandate.getCreatedDate())
            .fundTransferExchanges(getFundTransferExchanges(mandate))
            .pillar(mandate.getPillar())
            .address(mandate.getAddress())
            .email(mandate.getEmail())
            .phoneNumber(mandate.getPhoneNumber());
    addSelectionApplication(mandate, mandateDtoBuilder);
    addPaymentRateApplication(mandate, mandateDtoBuilder);
    return mandateDtoBuilder.build();
  }

  private MandateCommand getMandateCommand(MandateDto<?> mandateDto) {

    final var process = createMandateProcess(mandateDto, ApplicationType.CANCELLATION);
    mandateDtoBuilder.processId(process.getProcessId());

    return new MandateCommand(mandate);
  }

  private GenericMandateDto<CancellationMandateDetails> getCancellationDto(Mandate mandate) {
    final var genericMandateDtoBuilder =
        GenericMandateDto.<CancellationMandateDetails>builder()
            .id(mandate.getId())
            .createdDate(mandate.getCreatedDate())
            .address(mandate.getAddress())
            .email(mandate.getEmail())
            .phoneNumber(mandate.getPhoneNumber())
            .details(new CancellationMandateDetails(mandate.getApplicationTypeToCancel()))
        ;

    return genericMandateDtoBuilder.build();
  }

  private PaymentRateDto getPaymentRateDto(Mandate mandate) {
    final var mandateDtoBuilder =
        PaymentRateDto.builder()
            .id(mandate.getId())
            .createdDate(mandate.getCreatedDate())
            .rate(mandate.getPaymentRate())
            .address(mandate.getAddress())
            .email(mandate.getEmail())
            .phoneNumber(mandate.getPhoneNumber());

    final var process = createMandateProcess(mandate, ApplicationType.PAYMENT_RATE);
    mandateDtoBuilder.processId(process.getProcessId());
    return mandateDtoBuilder.build();
  }

  private void handleApplicationProcessResponse(ApplicationResponseDTO response) {
    response
        .getMandateResponses()
        .forEach(
            mandateProcessResult -> {
              log.info("Process result with id {} received", mandateProcessResult.getProcessId());
              MandateProcess process =
                  mandateProcessRepository.findOneByProcessId(mandateProcessResult.getProcessId());
              process.setSuccessful(mandateProcessResult.isSuccessful());
              process.setErrorCode(mandateProcessResult.getErrorCode());

              if (process.getErrorCode().isPresent()) {
                log.info(
                    "Process with id {} is {} with error code {}",
                    process.getId(),
                    process.isSuccessful().toString(),
                    process.getErrorCode().toString());
              } else {
                log.info(
                    "Process with id {} is {}", process.getId(), process.isSuccessful().toString());
              }

              mandateProcessRepository.save(process);
            });
  }

  @NotNull
  private List<MandateDto.MandateFundsTransferExchangeDTO> getFundTransferExchanges(
      Mandate mandate) {
    return mandate.getFundTransferExchangesBySourceIsin().entrySet().stream()
        .flatMap(
            entry -> {
              final var process = createMandateProcess(mandate, ApplicationType.TRANSFER);
              return entry.getValue().stream().map(it -> dtoFromExchange(process, it));
            })
        .collect(toList());
  }

  private MandateDto.MandateFundsTransferExchangeDTO dtoFromExchange(
      MandateProcess process, FundTransferExchange it) {
    return new MandateDto.MandateFundsTransferExchangeDTO(
        process.getProcessId(),
        it.getAmount(),
        it.getSourceFundIsin(),
        it.getTargetFundIsin(),
        it.getTargetPik());
  }

  private void addSelectionApplication(Mandate mandate, MandateDto.MandateDtoBuilder mandateDto) {
    if (mandate.getFutureContributionFundIsin().isPresent()) {
      final var process = createMandateProcess(mandate, ApplicationType.SELECTION);
      mandateDto.futureContributionFundIsin(mandate.getFutureContributionFundIsin().get());
      mandateDto.processId(process.getProcessId());
    }
  }

  private void addPaymentRateApplication(Mandate mandate, MandateDto.MandateDtoBuilder mandateDto) {
    if (mandate.isPaymentRateApplication()) {
      final var process = createMandateProcess(mandate, ApplicationType.PAYMENT_RATE);
      mandateDto.paymentRate(Optional.of(mandate.getPaymentRate()));
      mandateDto.processId(process.getProcessId());
    }
  }

  private MandateProcess createMandateProcess(Mandate mandate, ApplicationType type) {
    String processId = UUID.randomUUID().toString().replace("-", "");
    return mandateProcessRepository.save(
        MandateProcess.builder().mandate(mandate).processId(processId).type(type).build());
  }

  public boolean isFinished(Mandate mandate) {
    List<MandateProcess> processes = mandateProcessRepository.findAllByMandate(mandate);
    long finishedProcessCount =
        processes.stream().filter(process -> process.isSuccessful().isPresent()).count();

    return processes.size() == finishedProcessCount;
  }

  public ErrorsResponse getErrors(Mandate mandate) {
    List<MandateProcess> processes = mandateProcessRepository.findAllByMandate(mandate);
    return mandateProcessErrorResolver.getErrors(processes);
  }
}
