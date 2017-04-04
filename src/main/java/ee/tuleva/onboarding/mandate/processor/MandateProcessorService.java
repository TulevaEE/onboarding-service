package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import ee.tuleva.onboarding.mandate.content.MandateXmlService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.Session;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorService {

    private final MandateXmlService mandateXmlService;
    private final JmsTemplate jmsTemplate;
    private final MandateProcessRepository mandateProcessRepository;
    private final MandateProcessErrorResolver mandateProcessErrorResolver;

    public void start(User user, Mandate mandate) {
        log.info("Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());
        List<MandateXmlMessage> messages = mandateXmlService.getRequestContents(user, mandate.getId());

        log.info("Processing mandate {}", mandate.getId());
        messages.forEach( message -> {
            saveInitialMandateProcess(mandate, message);
            log.info("Sending message with id {} and type {}", message.getId(), message.getType().toString());
            jmsTemplate.send("MHUB.PRIVATE.IN", new MandateProcessorMessageCreator(message.getMessage()));
        });
    }

    private void saveInitialMandateProcess(Mandate mandate, MandateXmlMessage message) {
        mandateProcessRepository.save(
                MandateProcess.builder()
                        .mandate(mandate)
                        .processId(message.getId())
                        .type(message.getType())
                        .build()
        );
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

    class MandateProcessorMessageCreator implements MessageCreator {

        private String message;

        MandateProcessorMessageCreator(String message) {
            this.message = message;
        }

        @Override
        public javax.jms.Message createMessage(Session session) throws JMSException {
            return session.createTextMessage(message);
        }
    }

}
