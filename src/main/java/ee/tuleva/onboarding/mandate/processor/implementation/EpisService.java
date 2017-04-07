package ee.tuleva.onboarding.mandate.processor.implementation;

import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
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
         CreateProcessingCommand response = restTemplate.postForObject(url, getRequest(command), CreateProcessingCommand.class);
         log.info(response.toString());
      } catch (HttpClientErrorException e) {
         log.error(e.toString());
      }

   }

   private HttpEntity getRequest(CreateProcessingCommand command) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.add("authorization", "Bearer " + getToken());

      HttpEntity<CreateProcessingCommand> request = new HttpEntity<CreateProcessingCommand>(command, headers);
      return request;
   }

   private String getToken() {
      OAuth2AuthenticationDetails details =
              (OAuth2AuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();

      return details.getTokenValue();
   }

}
