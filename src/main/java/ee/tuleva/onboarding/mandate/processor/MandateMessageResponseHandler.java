package ee.tuleva.onboarding.mandate.processor;

import com.ibm.jms.JMSBytesMessage;
import ee.tuleva.epis.gen.AnswerType;
import ee.tuleva.epis.gen.EpisX5Type;
import ee.tuleva.epis.gen.EpisX6Type;
import ee.tuleva.epis.gen.MHubEnvelope;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateMessageResponseHandler {

    //TODO: test
    public MandateProcessResult getMandateProcessResponse(Message message) {
        log.info("Message received");
        String msg = null;

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
                // todo
                log.error(String.valueOf(message.getClass()));
            }
        } catch (JMSException e) {
            throw new RuntimeException();
        }

        MHubEnvelope envelope = unmarshallMessage(msg, MHubEnvelope.class);

        if (envelope != null) {
            String id = envelope.getBizMsg().getAppHdr().getBizMsgIdr();
            log.info("Getting response for message id {}", id);
            MHubResponse response = getResponse(envelope.getBizMsg().getAny());

            if(response.isSuccess()) {
                log.info("Message id {} returned successful response", id);
            } else {
                log.info("Message id {} returned error response with code {}", response.getErrorCode());
            }

            return MandateProcessResult.builder()
                    .processId(id)
                    .successful(response.isSuccess())
                    .errorCode(response.getErrorCode())
                    .build();

        } else {
            throw new RuntimeException("Can't parse response");
            // todo: try error types or handle error
        }
    }

    private MHubResponse getResponse(Element element) {
        MHubResponse response = new MHubResponse();
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("ee.tuleva.epis.gen");
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement jaxbElement = (JAXBElement) jaxbUnmarshaller.unmarshal(element);
            Object jaxbObject = jaxbElement.getValue();

            if (jaxbObject instanceof EpisX5Type) {
                response.setSuccess(((EpisX5Type) jaxbObject).getResponse().getResults().getResult().equals(AnswerType.OK));
                response.setErrorCode(((EpisX5Type) jaxbObject).getResponse().getResults().getResultCode());
                return response;
            } else if (jaxbObject instanceof EpisX6Type) {
                response.setSuccess((((EpisX6Type) jaxbObject).getResponse().getResults().getResult().equals(AnswerType.OK)));
                response.setErrorCode(((EpisX6Type) jaxbObject).getResponse().getResults().getResultCode());
                return response;
            }

            log.warn("Couldn't find message instance type");
            response.setSuccess(false);
            return response;
        } catch (JAXBException e) {
            log.warn("Exception on return message parsing", e);
            response.setSuccess(false);
            return response;
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

    @Getter
    @Setter
    private class MHubResponse {
        private boolean success;
        private Integer errorCode;
    }
}
