package ee.tuleva.onboarding.mandate.processor.implementation;

import com.ibm.jms.JMSBytesMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

// TODO: test
@Service
@Slf4j
public class MandateMessageResponseReader {

    String msg = null;

    public String getText(Message message) {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                msg = textMessage.getText();
            } else if (message instanceof JMSBytesMessage) {
                JMSBytesMessage bytesMessage = (JMSBytesMessage) message;

                int length = (int)bytesMessage.getBodyLength();
                byte[] byteArray = new byte[length];
                bytesMessage.readBytes(byteArray, length);
                msg = new String(byteArray);
            } else {
                log.error(String.valueOf(message.getClass()));
                throw new RuntimeException("Unknown message instance type");
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }

        return msg;
    }
}
