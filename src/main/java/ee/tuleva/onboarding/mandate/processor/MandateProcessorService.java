package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import ee.tuleva.onboarding.mandate.content.MandateXmlService;
import ee.tuleva.onboarding.mandate.processor.implementation.MhubProcessRunner;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorService {

    private final MandateXmlService mandateXmlService;
    private final MandateProcessRepository mandateProcessRepository;
    private final MandateProcessErrorResolver mandateProcessErrorResolver;
    private final MhubProcessRunner mhubProcessRunner;

    public void start(User user, Mandate mandate) {
        log.info("Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());

        List<MandateXmlMessage> messages = mandateXmlService.getRequestContents(user, mandate.getId());

        initializeProcesses(mandate, messages);
        mhubProcessRunner.process(messages);
    }

    private void initializeProcesses(Mandate mandate, List<MandateXmlMessage> messages) {
        messages.forEach( message -> {
            mandateProcessRepository.save(
                    MandateProcess.builder()
                            .mandate(mandate)
                            .processId(message.getId())
                            .type(message.getType())
                            .build()
            );
        });
    }

    public boolean isFinished(Mandate mandate) {
        List<MandateProcess> processes = mandateProcessRepository.findAllByMandate(mandate);
        Long finishedProcessCount = processes.stream().filter(process -> process.isSuccessful().isPresent()).count();

        return processes.size() == finishedProcessCount;
    }

    public ErrorsResponse getErrors(Mandate mandate) {
        List<MandateProcess> processes = mandateProcessRepository.findAllByMandate(mandate);
        return mandateProcessErrorResolver.getErrors(processes);
    }

}
