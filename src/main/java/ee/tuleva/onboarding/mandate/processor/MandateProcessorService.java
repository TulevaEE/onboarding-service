package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.mandate.MandateResponseDTO;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateApplicationType;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorService {

    private final MandateProcessRepository mandateProcessRepository;
    private final MandateProcessErrorResolver mandateProcessErrorResolver;
    private final EpisService episService;

    public void start(User user, Mandate mandate) {
        log.info("Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());

        val mandateDto = MandateDto.builder()
            .id(mandate.getId())
            .createdDate(mandate.getCreatedDate())
            .fundTransferExchanges(getFundTransferExchanges(mandate))
            .pillar(mandate.getPillar());
        addSelectionApplication(mandate, mandateDto);
        val response = episService.sendMandate(mandateDto.build());
        handleApplicationProcessResponse(response);
    }

    private void handleApplicationProcessResponse(MandateResponseDTO response) {
        response.getMandateResponses().forEach(mandateProcessResult -> {
            log.info("Process result with id {} received", mandateProcessResult.getProcessId());
            MandateProcess process = mandateProcessRepository.findOneByProcessId(mandateProcessResult.getProcessId());
            process.setSuccessful(mandateProcessResult.isSuccessful());
            process.setErrorCode(mandateProcessResult.getErrorCode());

            if (process.getErrorCode().isPresent()) {
                log.info("Process with id {} is {} with error code {}",
                    process.getId(),
                    process.isSuccessful().toString(),
                    process.getErrorCode().toString()
                );

            } else {
                log.info("Process with id {} is {}",
                    process.getId(),
                    process.isSuccessful().toString());
            }

            mandateProcessRepository.save(process);
        });

    }

    @NotNull
    private List<MandateDto.MandateFundsTransferExchangeDTO> getFundTransferExchanges(Mandate mandate) {
        return mandate.getFundTransferExchangesBySourceIsin().entrySet().stream().flatMap(entry -> {
            val process = createMandateProcess(mandate, MandateApplicationType.TRANSFER);
            return entry.getValue().stream()
                .map(it -> dtoFromExchange(process, it));
        }).collect(toList());
    }

    private MandateDto.MandateFundsTransferExchangeDTO dtoFromExchange(MandateProcess process, FundTransferExchange it) {
        return new MandateDto.MandateFundsTransferExchangeDTO(process.getProcessId(), it.getAmount(), it.getSourceFundIsin(), it.getTargetFundIsin());
    }

    private void addSelectionApplication(Mandate mandate, MandateDto.MandateDtoBuilder mandateDto) {
        if (mandate.getFutureContributionFundIsin().isPresent()) {
            val process = createMandateProcess(mandate, MandateApplicationType.SELECTION);
            mandateDto.futureContributionFundIsin(mandate.getFutureContributionFundIsin().get());
            mandateDto.processId(process.getProcessId());
        }
    }

    private MandateProcess createMandateProcess(Mandate mandate, MandateApplicationType type) {
        String processId = UUID.randomUUID().toString().replace("-", "");
        return mandateProcessRepository.save(
            MandateProcess.builder()
                .mandate(mandate)
                .processId(processId)
                .type(type)
                .build()
        );
    }

    public boolean isFinished(Mandate mandate) {
        List<MandateProcess> processes = mandateProcessRepository.findAllByMandate(mandate);
        long finishedProcessCount = processes.stream().filter(process -> process.isSuccessful().isPresent()).count();

        return processes.size() == finishedProcessCount;
    }

    public ErrorsResponse getErrors(Mandate mandate) {
        List<MandateProcess> processes = mandateProcessRepository.findAllByMandate(mandate);
        return mandateProcessErrorResolver.getErrors(processes);
    }

}
