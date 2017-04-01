package ee.tuleva.onboarding.mandate.processor;

import com.ibm.jms.JMSBytesMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorListener {

    private final MandateProcessRepository mandateProcessRepository;

    //TODO: test
    //TODO: identify message by ID and write into MandateMessageProcess entity
    @Bean
    public MessageListener processorListener() {
        return new MessageListener() {
            @Override
            public void onMessage(Message message) {
                log.info("Message received:");
                if (message instanceof TextMessage) {
                    TextMessage textMessage = (TextMessage) message;
                    try {
                        log.info(textMessage.getText());
                    } catch (JMSException e) {
                        throw new RuntimeException();
                    }
                } else if (message instanceof JMSBytesMessage) {
                    JMSBytesMessage bytesMessage = (JMSBytesMessage) message;
                    try {
                        int length = (int)bytesMessage.getBodyLength();
                        byte[] msg = new byte[length];
                        bytesMessage.readBytes(msg, length);
                        log.info(new String(msg));
                    } catch (JMSException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    log.info(String.valueOf(message.getClass()));
                }
            }
        };
    }

}
