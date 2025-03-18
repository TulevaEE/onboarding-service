package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.epis.MandateCommandResponseFixture.sampleMandateCommandResponse;
import static ee.tuleva.onboarding.epis.cancellation.CancellationFixture.sampleWithdrawalCancellation;
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.contribution.Contribution;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.epis.transaction.PensionTransaction;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

class EpisServiceTest {

  @Mock RestTemplate restTemplate;
  @Mock JwtTokenUtil jwtTokenUtil;

  @InjectMocks EpisService service;

  String sampleToken = "123";
  String sampleServiceToken = "123456";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    ReflectionTestUtils.setField(service, "episServiceUrl", "http://epis");

    when(jwtTokenUtil.generateServiceToken()).thenReturn(sampleServiceToken);

    Authentication sampleAuthentication = mock(Authentication.class);
    when(sampleAuthentication.getCredentials()).thenReturn(sampleToken);
    SecurityContextHolder.getContext().setAuthentication(sampleAuthentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void sendMandate() {
    var sampleMandate = sampleMandate();
    MandateDto mandateDto = MandateDto.builder().id(sampleMandate.getId()).build();

    doAnswer(
            invocation -> {
              String url = invocation.getArgument(0, String.class);
              assertEquals("http://epis/mandates", url, "Wrong endpoint for sendMandate()");
              HttpEntity<?> entity = invocation.getArgument(1, HttpEntity.class);
              assertTrue(doesHttpEntityContainToken(entity, sampleToken), "Missing user token");
              MandateDto body = (MandateDto) entity.getBody();
              assertEquals(sampleMandate.getId(), body.getId(), "MandateDto ID mismatch");
              return null;
            })
        .when(restTemplate)
        .postForObject(anyString(), any(HttpEntity.class), eq(ApplicationResponseDTO.class));

    service.sendMandate(mandateDto);
    verify(restTemplate)
        .postForObject(
            eq("http://epis/mandates"), any(HttpEntity.class), eq(ApplicationResponseDTO.class));
  }

  @Test
  void getApplications() {
    var person = samplePerson();
    ApplicationDTO[] responseBody = {ApplicationDTO.builder().build()};
    var resultEntity = new ResponseEntity<>(responseBody, OK);

    when(restTemplate.exchange(
            eq("http://epis/applications"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(ApplicationDTO[].class)))
        .thenReturn(resultEntity);

    List<ApplicationDTO> transferApplicationDTOList = service.getApplications(person);
    assertEquals(1, transferApplicationDTOList.size(), "Should return 1 ApplicationDTO");
  }

  @Test
  void getContactDetails() {
    var person = samplePerson();
    var fixture = contactDetailsFixture();
    var response = new ResponseEntity<>(fixture, OK);

    when(restTemplate.exchange(
            eq("http://epis/contact-details"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(ContactDetails.class)))
        .thenReturn(response);

    ContactDetails contactDetails = service.getContactDetails(person);
    assertEquals(fixture, contactDetails);
  }

  @Test
  void getContactDetailsWithProvidedJwtToken() {
    var person = samplePerson();
    var fixture = contactDetailsFixture();
    String customJwt = "customJWT";
    var response = new ResponseEntity<>(fixture, OK);

    when(restTemplate.exchange(
            eq("http://epis/contact-details"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, customJwt)),
            eq(ContactDetails.class)))
        .thenReturn(response);

    ContactDetails result = service.getContactDetails(person, customJwt);
    assertEquals(fixture, result);
  }

  @Test
  void getCashFlowStatementCallsTheRightEndpoint() {
    var person = samplePerson();
    var cashFlowStatement = cashFlowFixture();
    var responseEntity = new ResponseEntity<>(cashFlowStatement, OK);

    LocalDate fromDate = LocalDate.parse("2001-01-01");
    LocalDate toDate = LocalDate.parse("2018-01-01");

    String expectedUrl =
        "http://epis/account-cash-flow-statement?from-date=2001-01-01&to-date=2018-01-01";

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(CashFlowStatement.class)))
        .thenReturn(responseEntity);

    CashFlowStatement result = service.getCashFlowStatement(person, fromDate, toDate);
    assertEquals(cashFlowStatement, result, "Should return the fixture CashFlowStatement");
  }

  @Test
  void getsAccountStatement() {
    var person = samplePerson();
    FundBalanceDto[] responseBody = {FundBalanceDto.builder().isin("someIsin").build()};
    var responseEntity = new ResponseEntity<>(responseBody, OK);

    when(restTemplate.exchange(
            eq("http://epis/account-statement"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(FundBalanceDto[].class)))
        .thenReturn(responseEntity);

    List<FundBalanceDto> fundBalances = service.getAccountStatement(person);
    assertEquals(1, fundBalances.size());
    assertEquals("someIsin", fundBalances.get(0).getIsin());
  }

  @Test
  void getContributions() {
    var person = samplePerson();
    Contribution[] contributions = {Contribution.builder().amount(BigDecimal.TEN).build()};
    var responseEntity = new ResponseEntity<>(contributions, OK);

    when(restTemplate.exchange(
            eq("http://epis/contributions"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(Contribution[].class)))
        .thenReturn(responseEntity);

    List<Contribution> result = service.getContributions(person);
    assertEquals(1, result.size(), "Should return 1 contribution");
    assertEquals(BigDecimal.TEN, result.getFirst().amount());
  }

  @Test
  void getsFunds() {
    FundDto[] sampleFunds = {
      new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE)
    };
    var responseEntity = new ResponseEntity<>(sampleFunds, OK);

    when(restTemplate.exchange(
            eq("http://epis/funds"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(FundDto[].class)))
        .thenReturn(responseEntity);

    List<FundDto> funds = service.getFunds();
    assertEquals(1, funds.size(), "Expected 1 fund in the result");
    assertEquals(sampleFunds[0].getIsin(), funds.get(0).getIsin());
  }

  @Test
  void getsFundsEmptyArray() {
    FundDto[] emptyFunds = {};
    var responseEntity = new ResponseEntity<>(emptyFunds, OK);

    when(restTemplate.exchange(
            eq("http://epis/funds"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(FundDto[].class)))
        .thenReturn(responseEntity);

    List<FundDto> funds = service.getFunds();
    assertTrue(funds.isEmpty(), "Should return an empty list if no funds returned");
  }

  @Test
  void updatesContactDetails() {
    var person = samplePerson();
    var contactDetails = contactDetailsFixture();

    doAnswer(
            invocation -> {
              String url = invocation.getArgument(0, String.class);
              assertEquals(
                  "http://epis/contact-details",
                  url,
                  "Wrong endpoint for updating contact details");
              HttpEntity<?> entity = invocation.getArgument(1, HttpEntity.class);
              assertTrue(doesHttpEntityContainToken(entity, sampleToken), "Missing user token");
              ContactDetails body = (ContactDetails) entity.getBody();
              assertEquals(contactDetails.getPersonalCode(), body.getPersonalCode());
              return null;
            })
        .when(restTemplate)
        .postForObject(anyString(), any(HttpEntity.class), eq(ContactDetails.class));

    service.updateContactDetails(person, contactDetails);
    verify(restTemplate)
        .postForObject(
            eq("http://epis/contact-details"), any(HttpEntity.class), eq(ContactDetails.class));
  }

  @Test
  void getsNav() {
    String isin = "EE666";
    LocalDate date = LocalDate.parse("2018-10-20");
    String expectedUrl = "http://epis/navs/EE666?date=2018-10-20";

    NavDto navDto = mock(NavDto.class);
    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(
                entity ->
                    doesHttpEntityContainToken(entity, sampleServiceToken)), // uses service token
            eq(NavDto.class)))
        .thenReturn(new ResponseEntity<>(navDto, OK));

    NavDto result = service.getNav(isin, date);
    assertSame(navDto, result, "Should return the mock NavDto");
  }

  @Test
  void canSendCancellations() {
    var sampleCancellation = sampleWithdrawalCancellation();
    var mandateCommandResponse = sampleMandateCommandResponse("1", true, null, null);

    doAnswer(
            invocation -> {
              String url = invocation.getArgument(0, String.class);
              HttpEntity<?> entity = invocation.getArgument(1, HttpEntity.class);

              assertEquals("http://epis/mandates-v2", url);
              assertTrue(
                  doesHttpEntityContainToken(entity, sampleToken),
                  "Missing or incorrect Authorization header");

              MandateCommand<?> cmd = (MandateCommand<?>) entity.getBody();
              assertEquals(
                  sampleCancellation.getId(),
                  cmd.getMandateDto().getId(),
                  "The mandateDto ID must match the sampleCancellation ID");
              return mandateCommandResponse;
            })
        .when(restTemplate)
        .postForObject(
            eq("http://epis/mandates-v2"), any(HttpEntity.class), eq(MandateCommandResponse.class));

    var response = service.sendMandateV2(new MandateCommand<>("1", sampleCancellation));
    assertEquals("1", response.getProcessId());
    assertTrue(response.isSuccessful());
  }

  @Test
  void canGetFundPensionCalculation() {
    var person = samplePerson();
    var fundPensionCalculation = new FundPensionCalculationDto(20);

    when(restTemplate.exchange(
            eq("http://epis/fund-pension-calculation"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(FundPensionCalculationDto.class)))
        .thenReturn(ResponseEntity.ok(fundPensionCalculation));

    var response = service.getFundPensionCalculation(person);
    assertEquals(20, response.durationYears());
  }

  @Test
  void canGetFundPensionStatus() {
    var person = samplePerson();
    var fundPensionStatusDto = new FundPensionStatusDto(List.of(), List.of());

    when(restTemplate.exchange(
            eq("http://epis/fund-pension-status"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(FundPensionStatusDto.class)))
        .thenReturn(ResponseEntity.ok(fundPensionStatusDto));

    var response = service.getFundPensionStatus(person);
    assertSame(fundPensionStatusDto, response);
  }

  @Test
  void getArrestsBankruptciesPresent() {
    var person = samplePerson();
    var fixture = ArrestsBankruptciesDto.builder().build();

    when(restTemplate.exchange(
            eq("http://epis/arrests-bankruptcies"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleToken)),
            eq(ArrestsBankruptciesDto.class)))
        .thenReturn(ResponseEntity.ok(fixture));

    ArrestsBankruptciesDto result = service.getArrestsBankruptciesPresent(person);
    assertEquals(fixture, result);
  }

  @Test
  void getTransactions() {
    LocalDate startDate = LocalDate.of(2023, 1, 1);
    LocalDate endDate = LocalDate.of(2023, 1, 31);

    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis")
            .pathSegment("transactions")
            .queryParam("startDate", startDate)
            .queryParam("endDate", endDate)
            .toUriString();

    PensionTransaction[] sampleTransactions = {
      PensionTransaction.builder().personId("1234").build(),
      PensionTransaction.builder().personId("5678").build()
    };

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(
                entity ->
                    doesHttpEntityContainToken(entity, sampleServiceToken)), // uses service token
            eq(PensionTransaction[].class)))
        .thenReturn(ResponseEntity.ok(sampleTransactions));

    List<PensionTransaction> result = service.getTransactions(startDate, endDate);
    assertEquals(2, result.size(), "Should return 2 transactions");
    assertEquals("1234", result.get(0).getPersonId());
    assertEquals("5678", result.get(1).getPersonId());
  }

  @Test
  void getExchangeTransactions() {
    LocalDate startDate = LocalDate.of(2023, 1, 1);
    String securityFrom = "ISIN123";
    String securityTo = "ISIN456";
    boolean pikFlag = true;

    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis")
            .pathSegment("exchange-transactions")
            .queryParam("startDate", startDate)
            .queryParam("pikFlag", pikFlag)
            .queryParam("securityFrom", securityFrom)
            .queryParam("securityTo", securityTo)
            .toUriString();

    ExchangeTransactionDto[] sampleExchangeTransactionDtos = {
      ExchangeTransactionDto.builder().securityFrom(securityFrom).build(),
      ExchangeTransactionDto.builder().securityTo(securityTo).build()
    };

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(ExchangeTransactionDto[].class)))
        .thenReturn(ResponseEntity.ok(sampleExchangeTransactionDtos));

    var result =
        service.getExchangeTransactions(
            startDate, Optional.of(securityFrom), Optional.of(securityTo), pikFlag);

    assertEquals(2, result.size(), "Should return 2 exchange transactions");
    assertEquals(securityFrom, result.get(0).getSecurityFrom());
    assertEquals(securityTo, result.get(1).getSecurityTo());
  }

  @Test
  void clearCache() {
    var person = samplePerson();
    service.clearCache(person);
  }

  @Test
  void noAuthenticationPresent_throwsException() {
    SecurityContextHolder.clearContext();

    assertThrows(
        IllegalStateException.class,
        () -> service.getContactDetails(samplePerson()),
        "Should throw if no authentication present");
  }

  private boolean doesHttpEntityContainToken(HttpEntity<?> entity, String expectedToken) {
    HttpHeaders headers = entity.getHeaders();
    String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
    return ("Bearer " + expectedToken).equals(authHeader);
  }
}
