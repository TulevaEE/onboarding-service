package ee.tuleva.onboarding.mandate.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.jms.Message;
import javax.jms.MessageListener;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessorListener {

    private final MandateProcessRepository mandateProcessRepository;
    private final MandateMessageResponseHandler mandateMessageResponseHandler;

    @Bean
    public MessageListener processorListener() {
        return new MessageListener() {

            @Override
            public void onMessage(Message message) {
                MandateProcessResponse mandateProcessResponse =
                        mandateMessageResponseHandler.getMandateProcessResponse(message);

                // TODO: save message
            }
        };
    }

}
