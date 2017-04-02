package ee.tuleva.onboarding.kpr;

import com.ibm.jms.JMSBytesMessage;
import ee.tuleva.epis.gen.MHubEnvelope;
import ee.tuleva.onboarding.config.SocksConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.jms.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;

/**
 * Disable java assertions on this test (remove "-ea" from idea run conf), MQ Factory fails with assertions.
 */
@Slf4j
@ContextConfiguration(classes = {SocksConfiguration.class, MhubConnectivityTest.class, MhubConfiguration.class}, initializers = YamlFileApplicationContextInitializer.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class MhubConnectivityTest {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Test
    public void sendAndReceiveMessageToMhub() throws Exception {
        jmsTemplate.send(new MyMessageCreator("Message!"));
        // sleeping for some return messages
        Thread.sleep(10000);
    }

    @Bean
    public MessageListener createMyListener() {
        return new MessageListener() {
            @Override
            public void onMessage(Message message) {
                System.out.println("Message received:");
                if (message instanceof TextMessage) {
                    TextMessage textMessage = (TextMessage) message;
                    try {
                        System.out.println("TEXT:" + textMessage.getText());
                        MHubEnvelope envelope = unmarshallMessage(textMessage.getText(), MHubEnvelope.class);
                        if (envelope != null) {
                            System.out.println("OK envelope");
                        }
                    } catch (JMSException e) {
                        throw new RuntimeException();
                    }
                } else if (message instanceof JMSBytesMessage) {
                    JMSBytesMessage bytesMessage = (JMSBytesMessage) message;
                    try {
                        int length = (int)bytesMessage.getBodyLength();
                        byte[] msg = new byte[length];
                        bytesMessage.readBytes(msg, length);
                        System.out.println("BYTES:" + new String(msg));
                    } catch (JMSException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    System.out.println(message.getClass());
                }
            }
        };
    }


    public class MyMessageCreator implements MessageCreator {
        private String message;

        private MyMessageCreator(String message) {
            this.message = message;
        }

        @Override
        public Message createMessage(Session session) throws JMSException {
            return session.createTextMessage(message);
        }
    }


    private static <T> T unmarshallMessage(String msg, Class<T> expectedType) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("ee.tuleva.epis.gen");
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            Object ret = jaxbUnmarshaller.unmarshal(new StringReader(msg));

            if (ret instanceof JAXBElement) {
                JAXBElement jaxbElement = (JAXBElement) ret;
                Object valueObj = jaxbElement.getValue();

                if (expectedType.isInstance(valueObj)) {
                    return (T) valueObj;
                }
            }

            log.warn("Unable to parse Mhub message, unexpected return type!");
        } catch (JAXBException e) {
            log.warn("Unable to parse MHub message!" , e);
        }

        return null;
    }

}
