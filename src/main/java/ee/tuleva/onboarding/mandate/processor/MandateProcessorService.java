package ee.tuleva.onboarding.mandate.processor;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.GenericMandateSubmission;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission.FundTransferInstruction;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorService {

  private final MandateProcessRepository mandateProcessRepository;
  private final MandateProcessErrorResolver mandateProcessErrorResolver;
  private final MandateGateway mandateGateway;
  private final MandateRepository mandateRepository;

  public void start(User user, Mandate mandate) {
    log.info(
        "Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());

    if (mandate.supportsSubmission()) {
      final var response = mandateGateway.sendMandateV2(getMandateSubmissionCommand(mandate));
      handleMandateProcessResult(response);
    } else {
      final var response = mandateGateway.sendMandate(getLegacyMandateSubmission(mandate));
      handleMandateProcessResult(response);
    }
  }

  private LegacyMandateSubmission getLegacyMandateSubmission(Mandate mandate) {
    final var submissionBuilder =
        LegacyMandateSubmission.builder()
            .id(mandate.getIdOrThrow())
            .createdDate(
                requireNonNull(
                    mandate.getCreatedDate(),
                    "Mandate createdDate missing: mandateId=" + mandate.getId()))
            .fundTransferExchanges(getFundTransferExchanges(mandate))
            .pillar(mandate.getPillar())
            .address(mandate.getAddress())
            .email(mandate.getEmail())
            .phoneNumber(mandate.getPhoneNumber());
    addSelectionApplication(mandate, submissionBuilder);
    addPaymentRateApplication(mandate, submissionBuilder);
    return submissionBuilder.build();
  }

  private MandateSubmissionCommand<?> getMandateSubmissionCommand(Mandate mandate) {
    final var submission = mandate.toSubmission();
    final var process = createMandateProcess(submission, submission.details().getApplicationType());
    return new MandateSubmissionCommand<>(process.getProcessId(), submission);
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

  private void handleMandateProcessResult(MandateProcessResult result) {
    result
        .outcomes()
        .forEach(
            outcome -> {
              log.info("Process result with id {} received", outcome.processId());
              MandateProcess process =
                  mandateProcessRepository.findOneByProcessId(outcome.processId());
              process.setSuccessful(outcome.successful());
              process.setErrorCode(outcome.errorCode());

              saveFinalizedProcess(process);
            });
  }

  private List<FundTransferInstruction> getFundTransferExchanges(Mandate mandate) {
    return mandate.getFundTransferExchangesBySourceIsin().entrySet().stream()
        .flatMap(
            entry -> {
              final var process = createMandateProcess(mandate, ApplicationType.TRANSFER);
              return entry.getValue().stream().map(it -> instructionFromExchange(process, it));
            })
        .collect(toList());
  }

  private FundTransferInstruction instructionFromExchange(
      MandateProcess process, FundTransferExchange it) {
    return new FundTransferInstruction(
        process.getProcessId(),
        it.getAmount(),
        it.getSourceFundIsin(),
        it.getTargetFundIsin(),
        it.getTargetPik());
  }

  private void addSelectionApplication(
      Mandate mandate, LegacyMandateSubmission.LegacyMandateSubmissionBuilder submission) {
    if (mandate.getFutureContributionFundIsin().isPresent()) {
      final var process = createMandateProcess(mandate, ApplicationType.SELECTION);
      submission.futureContributionFundIsin(mandate.getFutureContributionFundIsin().get());
      submission.processId(process.getProcessId());
    }
  }

  private void addPaymentRateApplication(
      Mandate mandate, LegacyMandateSubmission.LegacyMandateSubmissionBuilder submission) {
    if (mandate.isPaymentRateApplication()) {
      final var process = createMandateProcess(mandate, ApplicationType.PAYMENT_RATE);
      submission.paymentRate(
          requireNonNull(
              mandate.getPaymentRate(), "Payment rate missing: mandateId=" + mandate.getId()));
      submission.processId(process.getProcessId());
    }
  }

  // TODO: delete this method when all mandates use GenericMandateSubmission
  private MandateProcess createMandateProcess(Mandate mandate, ApplicationType type) {
    String processId = UUID.randomUUID().toString().replace("-", "");
    return mandateProcessRepository.save(
        MandateProcess.builder().mandate(mandate).processId(processId).type(type).build());
  }

  private MandateProcess createMandateProcess(
      GenericMandateSubmission<?> submission, ApplicationType type) {
    String processId = UUID.randomUUID().toString().replace("-", "");
    Long mandateId =
        requireNonNull(
            submission.id(), "Mandate DTO has no id: type=" + submission.getMandateType());
    final Optional<Mandate> mandate = mandateRepository.findById(mandateId);

    if (mandate.isEmpty()) {
      throw new IllegalStateException("Mandate with id " + submission.id() + " not found");
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
