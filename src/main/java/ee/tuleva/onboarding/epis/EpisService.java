package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.mandate.MandateResponseDTO;
import ee.tuleva.onboarding.epis.mandate.TransferExchangeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.GET;

@Service
@Slf4j
@RequiredArgsConstructor
public class EpisService {

    private final String TRANSFER_APPLICATIONS_CACHE_NAME = "transferApplications";
    private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
    private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";
    private final String CASH_FLOW_STATEMENT_CACHE_NAME = "cashFlowStatement";
    private final String FUNDS_CACHE_NAME = "funds";

    private final RestTemplate restTemplate;

    @Value("${epis.service.url}")
    String episServiceUrl;

    @Cacheable(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode")
    public List<TransferExchangeDTO> getTransferApplications(Person person) {
        String url = episServiceUrl + "/exchanges";

        log.info("Getting exchanges from {} for {} {}",
            url, person.getFirstName(), person.getLastName());

        ResponseEntity<TransferExchangeDTO[]> response = restTemplate.exchange(
            url, GET, getHeadersEntity(), TransferExchangeDTO[].class);

        return asList(response.getBody());
    }

    @Cacheable(value = CASH_FLOW_STATEMENT_CACHE_NAME, key="{ #person.personalCode, #fromDate, #toDate }")
    public CashFlowStatement getCashFlowStatement(Person person, LocalDate fromDate, LocalDate toDate) {
        String url = UriComponentsBuilder
            .fromHttpUrl(episServiceUrl + "/account-cash-flow-statement")
            .queryParam("from-date", fromDate)
            .queryParam("to-date", toDate)
            .build()
            .toUriString();

        log.info("Getting cash flows from {}", url);
        return restTemplate.exchange(url, GET, getHeadersEntity(), CashFlowStatement.class).getBody();
    }

    @Caching(evict = {
        @CacheEvict(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode"),
    })
    public void clearCache(Person person) {
        log.info("Clearing cache for {}", person.getPersonalCode());
    }

    @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
    public UserPreferences getContactDetails(Person person) {
        String url = episServiceUrl + "/contact-details";

        log.info("Getting contact details from {} for {} {}",
            url, person.getFirstName(), person.getLastName());

        ResponseEntity<UserPreferences> response =
            restTemplate.exchange(url, GET, getHeadersEntity(), UserPreferences.class);

        return response.getBody();
    }

    @Cacheable(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
    public List<FundBalanceDto> getAccountStatement(Person person) {
        String url = episServiceUrl + "/account-statement";

        log.info("Getting account statement from {} for {} {}",
            url, person.getFirstName(), person.getLastName());

        ResponseEntity<FundBalanceDto[]> response =
            restTemplate.exchange(url, GET, getHeadersEntity(), FundBalanceDto[].class);

        return asList(response.getBody());
    }

    @Cacheable(value = FUNDS_CACHE_NAME, unless = "#result.isEmpty()")
    public List<FundDto> getFunds() {
        String url = episServiceUrl + "/funds";

        log.info("Getting funds from {}", url);

        ResponseEntity<FundDto[]> response =
            restTemplate.exchange(url, GET, getHeadersEntity(), FundDto[].class);

        return asList(response.getBody());
    }

    public NavDto getNav(String isin, LocalDate date) {
        String url = episServiceUrl + "/v1/navs/" + isin + "?date=" + date;
        return restTemplate.exchange(url, GET, getHeadersEntity(), NavDto.class).getBody();
    }

    public MandateResponseDTO sendMandate(MandateDto mandate) {
        String url = episServiceUrl + "/mandates";

        return restTemplate.postForObject(
            url, new HttpEntity<>(mandate, getHeaders()), MandateResponseDTO.class);
    }

    @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
    public UserPreferences updateContactDetails(Person person, UserPreferences contactDetails) {
        String url = episServiceUrl + "/contact-details";

        log.info("Updating contact details for {}", contactDetails.getPersonalCode());

        return restTemplate.postForObject(url, new HttpEntity<>(contactDetails, getHeaders()), UserPreferences.class);
    }

    @NotNull
    private HttpEntity getHeadersEntity() {
        return new HttpEntity(getHeaders());
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
