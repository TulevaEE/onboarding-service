package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.mandate.TransferExchangeDTO;
import ee.tuleva.onboarding.epis.account.FundBalance;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static java.util.Arrays.asList;

@Service
@Slf4j
@RequiredArgsConstructor
public class EpisService {

   private final String TRANSFER_APPLICATIONS_CACHE_NAME = "transferApplications";
   private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
   private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";

   private final RestTemplate restTemplate;

   @Value("${epis.service.url}")
   String episServiceUrl;

   public void process(List<MandateXmlMessage> messages) {

      String url = episServiceUrl + "/processing";

      log.info("Submitting process to {} " + url);

      CreateProcessingCommand command = new CreateProcessingCommand(messages);

      try {
         restTemplate.postForObject(url, getProcessingRequest(command), CreateProcessingCommand.class);
      } catch (HttpStatusCodeException e) {
         throw new EpisServiceException("Error processing mandate messages through epis-service", e);
      }
   }

   @Cacheable(value=TRANSFER_APPLICATIONS_CACHE_NAME, key="#person.personalCode")
   public List<TransferExchangeDTO> getTransferApplications(Person person) {
      String url = episServiceUrl + "/exchanges";

      log.info("Getting exchanges from {} for {} {}",
              url, person.getFirstName(), person.getLastName());

      ResponseEntity<TransferExchangeDTO[]> response = restTemplate.exchange(
              url, HttpMethod.GET, new HttpEntity(getHeaders()), TransferExchangeDTO[].class);

      return asList(response.getBody());
   }

   public void clearCache(Person person) {
      clearTransferApplicationsCache(person);
      clearContactDetailsCache(person);
      clearAccountStatementCache(person);
   }

   @CacheEvict(value=TRANSFER_APPLICATIONS_CACHE_NAME, key="#person.personalCode")
   public void clearTransferApplicationsCache(Person person) {
      log.info("Clearing exchanges cache for {} {}",
          person.getFirstName(), person.getLastName());
   }

   @Cacheable(value=CONTACT_DETAILS_CACHE_NAME, key="#person.personalCode")
   public UserPreferences getContactDetails(Person person) {
      String url = episServiceUrl + "/contact-details";

      log.info("Getting contact details from {} for {} {}",
          url, person.getFirstName(), person.getLastName());

      ResponseEntity<UserPreferences> response = restTemplate.exchange(
          url, HttpMethod.GET, new HttpEntity(getHeaders()), UserPreferences.class);

      return response.getBody();
   }

   @CacheEvict(value=CONTACT_DETAILS_CACHE_NAME, key="#person.personalCode")
   public void clearContactDetailsCache(Person person) {
      log.info("Clearing contact cache for {} {}",
          person.getFirstName(), person.getLastName());
   }

   private HttpEntity getProcessingRequest(CreateProcessingCommand command) {
      return new HttpEntity<>(command, getHeaders());
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

   @Cacheable(value= ACCOUNT_STATEMENT_CACHE_NAME, key="#person.personalCode")
   public List<FundBalance> getAccountStatement(Person person) {
      String url = episServiceUrl + "/account-statement";

      log.info("Getting account statement from {} for {} {}",
          url, person.getFirstName(), person.getLastName());

      ResponseEntity<FundBalance[]> response = restTemplate.exchange(
          url, HttpMethod.GET, new HttpEntity(getHeaders()), FundBalance[].class);

      return asList(response.getBody());
   }

   @CacheEvict(value= ACCOUNT_STATEMENT_CACHE_NAME, key="#person.personalCode")
   public void clearAccountStatementCache(Person person) {
      log.info("Clearing account statement cache for {} {}",
          person.getFirstName(), person.getLastName());
   }

}
