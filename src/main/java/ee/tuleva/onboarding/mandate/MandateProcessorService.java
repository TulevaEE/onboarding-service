package ee.tuleva.onboarding.mandate;

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

    public void start(User user, Mandate mandate) {
        log.info("Start mandate processing user id {} and mandate id {}", user.getId(), mandate.getId());
        List<MandateXmlMessage> messages = mandateXmlService.getRequestContents(user, mandate.getId());

        messages.forEach( message -> {
            jmsTemplate.send("MHUB.PRIVATE.IN", new MandateProcessorMessageCreator(message.getMessage()));
        });
    }

    public boolean isFinished() {

        return false;
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
