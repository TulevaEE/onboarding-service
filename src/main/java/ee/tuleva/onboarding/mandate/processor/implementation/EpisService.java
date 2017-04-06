package ee.tuleva.onboarding.mandate.processor.implementation;

import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

// TODO: butt ugly proof of concept
@Service
@Slf4j
@RequiredArgsConstructor
public class EpisService {

   private final RestTemplate restTemplate;

   @Value("${epis.service.url}")
   String episServiceUrl;

   public void process(List<MandateXmlMessage> messages) {

      String url = episServiceUrl + "/processing";

      log.info("Submitting process to {} " + url);

      CreateProcessingCommand command = new CreateProcessingCommand(messages);

      try {
         CreateMandateCommand response = restTemplate.postForObject(url, command, CreateMandateCommand.class);
         log.info(response.toString());
      } catch (HttpClientErrorException e) {
         log.error(e.toString());
      }

   }

}
