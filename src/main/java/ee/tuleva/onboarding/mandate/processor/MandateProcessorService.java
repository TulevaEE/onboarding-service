package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.MandateDTO;
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

        val mandateDTO = MandateDTO.builder();
        mandateDTO.id(mandate.getId());
        mandateDTO.createdDate(mandate.getCreatedDate());
        addSelectionApplication(mandate, mandateDTO);
        mandateDTO.fundTransferExchanges(getFundTransferExchanges(mandate));
        val response = episService.sendMandate(mandateDTO.build());
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
    private List<MandateDTO.MandateFundsTransferExchangeDTO> getFundTransferExchanges(Mandate mandate) {
        return mandate.getPrintableFundExchangeStructure().entrySet().stream().flatMap(entry -> {
            val process = createMandateProcess(mandate, MandateApplicationType.TRANSFER, mandate.getId(), entry.getKey());
            return entry.getValue().stream()
                .map(it -> dtoFromExchange(process, it));
        }).collect(toList());
    }

    private MandateDTO.MandateFundsTransferExchangeDTO dtoFromExchange(MandateProcess process, FundTransferExchange it) {
        return new MandateDTO.MandateFundsTransferExchangeDTO(process.getProcessId(), it.getAmount(), it.getSourceFundIsin(), it.getTargetFundIsin());
    }

    private void addSelectionApplication(Mandate mandate, MandateDTO.MandateDTOBuilder mandateDTO) {
        if (mandate.getFutureContributionFundIsin().isPresent()) {
            val process = createMandateProcess(mandate, MandateApplicationType.SELECTION, mandate.getId(), "");
            mandateDTO.futureContributionFundIsin(mandate.getFutureContributionFundIsin().get());
            mandateDTO.processId(process.getProcessId());
        }
    }

    private MandateProcess createMandateProcess(Mandate mandate, MandateApplicationType type, Long id, String isin) {
        String processId = UUID.nameUUIDFromBytes((type.toString() + id + isin).getBytes())
            .toString().replace("-", "");
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
