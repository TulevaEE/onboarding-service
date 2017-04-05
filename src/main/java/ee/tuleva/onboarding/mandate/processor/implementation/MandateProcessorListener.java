package ee.tuleva.onboarding.mandate.processor.implementation;

import ee.tuleva.onboarding.mandate.processor.MandateProcess;
import ee.tuleva.onboarding.mandate.processor.MandateProcessRepository;
import ee.tuleva.onboarding.mandate.processor.MandateProcessResult;
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
                log.info("Process result received");
                MandateProcessResult mandateProcessResult =
                        mandateMessageResponseHandler.getMandateProcessResponse(message);

                log.info("Process result with id {} received", mandateProcessResult.getProcessId());
                MandateProcess process = mandateProcessRepository.findOneByProcessId(mandateProcessResult.getProcessId());

                process.setSuccessful(mandateProcessResult.isSuccessful());
                process.setErrorCode(mandateProcessResult.getErrorCode().orElse(null));

                if(process.getErrorCode().isPresent()) {
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
            }
        };
    }

}
