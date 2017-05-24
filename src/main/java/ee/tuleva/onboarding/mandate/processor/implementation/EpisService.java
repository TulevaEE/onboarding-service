package ee.tuleva.onboarding.mandate.processor.implementation;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferExchangeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

// FIXME: butt ugly proof of concept
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
         CreateProcessingCommand response = restTemplate.postForObject(url, getProcessingRequest(command), CreateProcessingCommand.class);
      } catch (HttpClientErrorException e) {
         log.error(e.toString());
      }

   }

   @Cacheable(value="transferApplications", key="#person.personalCode")
   public List<TransferExchangeDTO> getTransferApplications(Person person) {
      String url = episServiceUrl + "/exchanges";

      log.info("Getting exchanges from {} for {} {}",
              url, person.getFirstName(), person.getLastName());

      ResponseEntity<TransferExchangeDTO[]> response = restTemplate.exchange(
              url, HttpMethod.GET, new HttpEntity(getHeaders()), TransferExchangeDTO[].class);

      return Arrays.asList(response.getBody());
   }

   private HttpEntity getProcessingRequest(CreateProcessingCommand command) {
      HttpHeaders headers = getHeaders();

      HttpEntity<CreateProcessingCommand> request = new HttpEntity<CreateProcessingCommand>(command, headers);
      return request;
   }

   private HttpHeaders getHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.add("authorization", "Bearer " + getToken());

      return headers;
   }

   private String getToken() {
      OAuth2AuthenticationDetails details =
              (OAuth2AuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();

      return details.getTokenValue();
   }

}
