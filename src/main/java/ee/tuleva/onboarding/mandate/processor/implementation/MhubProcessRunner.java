package ee.tuleva.onboarding.mandate.processor.implementation;

import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import ee.tuleva.onboarding.mandate.processor.MandateProcess;
import ee.tuleva.onboarding.mandate.processor.MandateProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.Session;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MhubProcessRunner {

    private final JmsTemplate jmsTemplate;
    private final MandateProcessRepository mandateProcessRepository;

    @Async
    public void process(List<MandateXmlMessage> messages) {
        messages.forEach( message -> {
            MandateProcess process = mandateProcessRepository.findOneByProcessId(message.getId());

            log.info("Sending message with id {} and type {}", message.getId(), message.getType().toString());
            jmsTemplate.send("MHUB.PRIVATE.IN", new MandateProcessorMessageCreator(message.getMessage()));
            log.info("Sent message with id {}", message.getId());
            waitForProcessToFinish(process);
        });
    }

    // EPIS message que NEEDS SYNCHRONIZATION,
    // before you send a new message, older one needs a response
    // otherwise it responds with a technical error
    private void waitForProcessToFinish(MandateProcess process) {
        while(isProcessFinished(process) != true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isProcessFinished(MandateProcess inputProcess) {
        MandateProcess process = mandateProcessRepository.findOneByProcessId(inputProcess.getProcessId());
        return process.isSuccessful().isPresent();
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