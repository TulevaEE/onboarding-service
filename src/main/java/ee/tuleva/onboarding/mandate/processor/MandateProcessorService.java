package ee.tuleva.onboarding.mandate.processor;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse;
import ee.tuleva.onboarding.epis.payment.rate.PaymentRateDto;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateRepository;
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
  private final MandateRepository mandateRepository;

  public void start(User user, Mandate mandate) {
    log.info(
        "Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());

    if (mandate.supportsGenericMandateDto()) {
      final var response =
          episService.sendMandateV2(getMandateCommand(mandate.getGenericMandateDto()));
      handleMandateCommandResponse(response);
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

  private MandateCommand<?> getMandateCommand(GenericMandateDto<?> mandateDto) {
    final var process =
        createMandateProcess(mandateDto, mandateDto.getDetails().getApplicationType());
    return new MandateCommand(process.getProcessId(), mandateDto);
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

  private void handleMandateCommandResponse(MandateCommandResponse response) {
    log.info("Process result with id {} received", response.getProcessId());
    MandateProcess process = mandateProcessRepository.findOneByProcessId(response.getProcessId());
    process.setSuccessful(response.isSuccessful());
    process.setErrorCode(response.getErrorCode());

    saveFinalizedProcess(process);
  }

  private void saveFinalizedProcess(MandateProcess process) {
    if (process.getErrorCode().isPresent()) {
      log.error(
          "Process is not successful: processId={}, isSuccessful={} errorCode={}",
          process.getId(),
          process.isSuccessful(),
          process.getErrorCode());
    } else {
      log.info(
          "Process is successful: processId={}, isSuccessful={}",
          process.getId(),
          process.isSuccessful());
    }

    mandateProcessRepository.save(process);
  }

  // TODO: delete when all mandates have migrated to GenericMandateTdo
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

              saveFinalizedProcess(process);
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

  // TODO: delete this method when all mandates use GenericMandateDto
  private MandateProcess createMandateProcess(Mandate mandate, ApplicationType type) {
    String processId = UUID.randomUUID().toString().replace("-", "");
    return mandateProcessRepository.save(
        MandateProcess.builder().mandate(mandate).processId(processId).type(type).build());
  }

  private MandateProcess createMandateProcess(
      GenericMandateDto<?> genericMandateDto, ApplicationType type) {
    String processId = UUID.randomUUID().toString().replace("-", "");
    final Optional<Mandate> mandate = mandateRepository.findById(genericMandateDto.getId());

    if (mandate.isEmpty()) {
      throw new IllegalStateException(
          "Mandate with id " + genericMandateDto.getId() + " not found");
    } else {
      return mandateProcessRepository.save(
          MandateProcess.builder()
              .mandate((mandate.get()))
              .processId(processId)
              .type(type)
              .build());
    }
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
