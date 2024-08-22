package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDto;
import ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

// import static ee.tuleva.onboarding.mandate.MandateFixture.sampleGenericMandateCreationDto;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
public class GenericMandateIntegrationTest {


  @Autowired
  private RestTemplate restTemplate;

  static <TDetails extends MandateDetails> GenericMandateCreationDto sampleGenericMandateCreationDto(TDetails details) {
    return GenericMandateCreationDto.builder().details(details).build();
  }

//  @Autowired
//  private CacheManager cacheManager;
//
//  private MockServerClient mockServerClient;

  private void setUpSecurityContext() {
//    SecurityContext sc = SecurityContextHolder.createEmptyContext()
//    TestingAuthenticationToken authentication = new TestingAuthenticationToken("test", "dummy")
//    sc.authentication = authentication
//    SecurityContextHolder.context = sc
  }

  @BeforeEach
  void setUp() {
    setUpSecurityContext();
  }

//  def cleanup() {
//    SecurityContextHolder.clearContext()
//    cacheManager.cacheNames.stream().forEach(cache -> cacheManager.getCache(cache).clear())
//  }

  @Test
  public void testMandateCreation() {
    String url = "/v1/mandates/generic";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);

//    var aDto = sampleGenericMandateCreationDto(new WithdrawalCancellationMandateDetails());
    var aDto = GenericMandateCreationDto.builder().details(new WithdrawalCancellationMandateDetails()).build();

    HttpEntity<GenericMandateCreationDto<?>> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<Mandate> response = restTemplate.postForEntity(url, request, Mandate.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();



    //0. bootstrap the data
    //1. call endpoint with json and a token
    //2. assert the respojnse

  }

  static class Config {
    @Bean
    @ConditionalOnMissingBean(value = ErrorAttributes.class)
    DefaultErrorAttributes errorAttributes() {
      return new DefaultErrorAttributes();
    }
  }
}
