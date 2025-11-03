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
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.contribution.Contribution;
import ee.tuleva.onboarding.contribution.ThirdPillarContribution;
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
import ee.tuleva.onboarding.epis.transaction.*;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class EpisServiceTest {

  @Mock RestTemplate restTemplate;
  @Mock JwtTokenUtil jwtTokenUtil;

  @InjectMocks EpisService service;

  String sampleUserToken = "123";
  String sampleServiceToken = "123456";
  Person samplePerson = samplePerson();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "episServiceUrl", "http://epis");
    ReflectionTestUtils.setField(service, "episServiceLongRequestUrl", "http://epis");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void setupUserAuthentication() {
    Authentication sampleAuthentication = mock(Authentication.class);
    lenient().when(sampleAuthentication.getCredentials()).thenReturn(sampleUserToken);
    SecurityContextHolder.getContext().setAuthentication(sampleAuthentication);
  }

  private void setupServiceTokenGeneration() {
    when(jwtTokenUtil.generateServiceToken()).thenReturn(sampleServiceToken);
  }

  @Test
  void sendMandate() {
    // given
    setupUserAuthentication();
    var mandate = sampleMandate();
    MandateDto mandateDto = MandateDto.builder().id(mandate.getId()).build();

    doAnswer(
            invocation -> {
              String url = invocation.getArgument(0, String.class);
              assertEquals("http://epis/mandates", url);
              HttpEntity<?> entity = invocation.getArgument(1, HttpEntity.class);
              assertTrue(doesHttpEntityContainToken(entity, sampleUserToken));
              MandateDto body = (MandateDto) entity.getBody();
              assertEquals(mandate.getId(), body.getId());
              return mock(ApplicationResponseDTO.class);
            })
        .when(restTemplate)
        .postForObject(anyString(), any(HttpEntity.class), eq(ApplicationResponseDTO.class));

    // when
    service.sendMandate(mandateDto);

    // then
    verify(restTemplate)
        .postForObject(
            eq("http://epis/mandates"), any(HttpEntity.class), eq(ApplicationResponseDTO.class));
  }

  @Test
  void getApplications() {
    // given
    setupUserAuthentication();
    ApplicationDTO[] responseBody = {ApplicationDTO.builder().build()};
    var resultEntity = new ResponseEntity<>(responseBody, OK);

    when(restTemplate.exchange(
            eq("http://epis/applications"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(ApplicationDTO[].class)))
        .thenReturn(resultEntity);

    // when
    List<ApplicationDTO> transferApplicationDTOList = service.getApplications(samplePerson);

    // then
    assertEquals(1, transferApplicationDTOList.size());
  }

  @Test
  void getContactDetails() {
    // given
    setupUserAuthentication();
    var fixture = contactDetailsFixture();
    var response = new ResponseEntity<>(fixture, OK);

    when(restTemplate.exchange(
            eq("http://epis/contact-details"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(ContactDetails.class)))
        .thenReturn(response);

    // when
    ContactDetails contactDetails = service.getContactDetails(samplePerson);

    // then
    assertEquals(fixture, contactDetails);
  }

  @Test
  void getContactDetailsWithProvidedJwtToken() {
    // given
    setupUserAuthentication();
    var fixture = contactDetailsFixture();
    String customJwt = "customJWT";
    var response = new ResponseEntity<>(fixture, OK);

    when(restTemplate.exchange(
            eq("http://epis/contact-details"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, customJwt)),
            eq(ContactDetails.class)))
        .thenReturn(response);

    // when
    ContactDetails result = service.getContactDetails(samplePerson, customJwt);

    // then
    assertEquals(fixture, result);
  }

  @Test
  void getCashFlowStatementCallsTheRightEndpoint() {
    // given
    setupUserAuthentication();
    var cashFlowStatement = cashFlowFixture();
    var responseEntity = new ResponseEntity<>(cashFlowStatement, OK);
    LocalDate fromDate = LocalDate.parse("2001-01-01");
    LocalDate toDate = LocalDate.parse("2018-01-01");
    String expectedUrl =
        "http://epis/account-cash-flow-statement?from-date=2001-01-01&to-date=2018-01-01";

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(CashFlowStatement.class)))
        .thenReturn(responseEntity);

    // when
    CashFlowStatement result = service.getCashFlowStatement(samplePerson, fromDate, toDate);

    // then
    assertEquals(cashFlowStatement, result);
  }

  @Test
  void getsAccountStatement() {
    // given
    setupUserAuthentication();
    FundBalanceDto[] responseBody = {FundBalanceDto.builder().isin("someIsin").build()};
    var responseEntity = new ResponseEntity<>(responseBody, OK);

    when(restTemplate.exchange(
            eq("http://epis/account-statement"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(FundBalanceDto[].class)))
        .thenReturn(responseEntity);

    // when
    List<FundBalanceDto> fundBalances = service.getAccountStatement(samplePerson, null, null);

    // then
    assertEquals(1, fundBalances.size());
    assertEquals("someIsin", fundBalances.get(0).getIsin());
  }

  @Test
  void getContributions() {
    // given
    setupUserAuthentication();
    Contribution[] contributions = {
      ThirdPillarContribution.builder().amount(BigDecimal.TEN).build()
    };
    var responseEntity = new ResponseEntity<>(contributions, OK);

    when(restTemplate.exchange(
            eq("http://epis/contributions"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(Contribution[].class)))
        .thenReturn(responseEntity);

    // when
    List<Contribution> result = service.getContributions(samplePerson);

    // then
    assertEquals(1, result.size());
    assertEquals(BigDecimal.TEN, result.getFirst().amount());
  }

  @Test
  void getsFunds() {
    // given
    setupUserAuthentication();
    FundDto[] sampleFunds = {
      new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE)
    };
    var responseEntity = new ResponseEntity<>(sampleFunds, OK);

    when(restTemplate.exchange(
            eq("http://epis/funds"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(FundDto[].class)))
        .thenReturn(responseEntity);

    // when
    List<FundDto> funds = service.getFunds();

    // then
    assertEquals(1, funds.size());
    assertEquals(sampleFunds[0].getIsin(), funds.get(0).getIsin());
  }

  @Test
  void getsFundsEmptyArray() {
    // given
    setupUserAuthentication();
    FundDto[] emptyFunds = {};
    var responseEntity = new ResponseEntity<>(emptyFunds, OK);

    when(restTemplate.exchange(
            eq("http://epis/funds"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(FundDto[].class)))
        .thenReturn(responseEntity);

    // when
    List<FundDto> funds = service.getFunds();

    // then
    assertTrue(funds.isEmpty());
  }

  @Test
  void updatesContactDetails() {
    // given
    setupUserAuthentication();
    var contactDetails = contactDetailsFixture();

    doAnswer(
            invocation -> {
              String url = invocation.getArgument(0, String.class);
              assertEquals("http://epis/contact-details", url);
              HttpEntity<?> entity = invocation.getArgument(1, HttpEntity.class);
              assertTrue(doesHttpEntityContainToken(entity, sampleUserToken));
              ContactDetails body = (ContactDetails) entity.getBody();
              assertEquals(contactDetails.getPersonalCode(), body.getPersonalCode());
              return mock(ContactDetails.class);
            })
        .when(restTemplate)
        .postForObject(anyString(), any(HttpEntity.class), eq(ContactDetails.class));

    // when
    service.updateContactDetails(samplePerson, contactDetails);

    // then
    verify(restTemplate)
        .postForObject(
            eq("http://epis/contact-details"), any(HttpEntity.class), eq(ContactDetails.class));
  }

  @Test
  void getsNav() {
    // given
    setupServiceTokenGeneration();
    String isin = "EE666";
    LocalDate date = LocalDate.parse("2018-10-20");
    String expectedUrl = "http://epis/navs/EE666?date=2018-10-20";
    NavDto navDto = mock(NavDto.class);

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(NavDto.class)))
        .thenReturn(new ResponseEntity<>(navDto, OK));

    // when
    NavDto result = service.getNav(isin, date);

    // then
    assertSame(navDto, result);
  }

  @Test
  void canSendCancellations() {
    // given
    setupUserAuthentication();
    var sampleCancellation = sampleWithdrawalCancellation();
    var mandateCommandResponse = sampleMandateCommandResponse("1", true, null, null);

    doAnswer(
            invocation -> {
              HttpEntity<?> entity = invocation.getArgument(1, HttpEntity.class);
              assertTrue(doesHttpEntityContainToken(entity, sampleUserToken));
              MandateCommand<?> cmd = (MandateCommand<?>) entity.getBody();
              assertEquals(sampleCancellation.getId(), cmd.getMandateDto().getId());
              return mandateCommandResponse;
            })
        .when(restTemplate)
        .postForObject(
            eq("http://epis/mandates-v2"), any(HttpEntity.class), eq(MandateCommandResponse.class));

    // when
    var response = service.sendMandateV2(new MandateCommand<>("1", sampleCancellation));

    // then
    assertEquals("1", response.getProcessId());
    assertTrue(response.isSuccessful());
  }

  @Test
  void canGetFundPensionCalculation() {
    // given
    setupUserAuthentication();
    var fundPensionCalculation = new FundPensionCalculationDto(20);

    when(restTemplate.exchange(
            eq("http://epis/fund-pension-calculation"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(FundPensionCalculationDto.class)))
        .thenReturn(ResponseEntity.ok(fundPensionCalculation));

    // when
    var response = service.getFundPensionCalculation(samplePerson);

    // then
    assertEquals(20, response.durationYears());
  }

  @Test
  void canGetFundPensionStatus() {
    // given
    setupUserAuthentication();
    var fundPensionStatusDto = new FundPensionStatusDto(List.of(), List.of());

    when(restTemplate.exchange(
            eq("http://epis/fund-pension-status"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(FundPensionStatusDto.class)))
        .thenReturn(ResponseEntity.ok(fundPensionStatusDto));

    // when
    var response = service.getFundPensionStatus(samplePerson);

    // then
    assertSame(fundPensionStatusDto, response);
  }

  @Test
  void getArrestsBankruptciesPresent() {
    // given
    setupUserAuthentication();
    var fixture = ArrestsBankruptciesDto.builder().build();

    when(restTemplate.exchange(
            eq("http://epis/arrests-bankruptcies"),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleUserToken)),
            eq(ArrestsBankruptciesDto.class)))
        .thenReturn(ResponseEntity.ok(fixture));

    // when
    ArrestsBankruptciesDto result = service.getArrestsBankruptciesPresent(samplePerson);

    // then
    assertEquals(fixture, result);
  }

  @Test
  void getTransactions() {
    // given
    setupServiceTokenGeneration();
    LocalDate startDate = LocalDate.of(2023, 1, 1);
    LocalDate endDate = LocalDate.of(2023, 1, 31);
    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis")
            .pathSegment("transactions")
            .queryParam("startDate", startDate.toString())
            .queryParam("endDate", endDate.toString())
            .toUriString();
    ThirdPillarTransactionDto[] sampleTransactions = {
      ThirdPillarTransactionDto.builder().personId("1234").build(),
      ThirdPillarTransactionDto.builder().personId("5678").build()
    };

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(ThirdPillarTransactionDto[].class)))
        .thenReturn(ResponseEntity.ok(sampleTransactions));

    // when
    List<ThirdPillarTransactionDto> result = service.getTransactions(startDate, endDate);

    // then
    assertEquals(2, result.size());
    assertEquals("1234", result.get(0).getPersonId());
  }

  @Test
  void getExchangeTransactions() {
    // given
    setupServiceTokenGeneration();
    LocalDate startDate = LocalDate.of(2023, 1, 1);
    String securityFrom = "ISIN123";
    String securityTo = "ISIN456";
    boolean pikFlag = true;
    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis")
            .pathSegment("exchange-transactions")
            .queryParam("startDate", startDate.toString())
            .queryParam("pikFlag", pikFlag)
            .queryParam("securityFrom", securityFrom)
            .queryParam("securityTo", securityTo)
            .toUriString();
    ExchangeTransactionDto[] sampleDtos = {
      ExchangeTransactionDto.builder().securityFrom(securityFrom).build(),
      ExchangeTransactionDto.builder().securityTo(securityTo).build()
    };

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(ExchangeTransactionDto[].class)))
        .thenReturn(ResponseEntity.ok(sampleDtos));

    // when
    var result =
        service.getExchangeTransactions(
            startDate, Optional.of(securityFrom), Optional.of(securityTo), pikFlag);

    // then
    assertEquals(2, result.size());
    assertEquals(securityFrom, result.get(0).getSecurityFrom());
  }

  @Test
  void getFundTransactions() {
    // given
    setupServiceTokenGeneration();
    String isin = "FUNDISIN123";
    LocalDate fromDate = LocalDate.of(2024, 1, 1);
    LocalDate toDate = LocalDate.of(2024, 3, 31);
    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis")
            .pathSegment("fund-transactions")
            .queryParam("isin", isin)
            .queryParam("fromDate", fromDate.toString())
            .queryParam("toDate", toDate.toString())
            .toUriString();
    FundTransactionDto[] sampleFundTransactions = {
      FundTransactionDto.builder().amount(BigDecimal.valueOf(100)).build(),
    };

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(FundTransactionDto[].class)))
        .thenReturn(ResponseEntity.ok(sampleFundTransactions));

    // when
    List<FundTransactionDto> result = service.getFundTransactions(isin, fromDate, toDate);

    // then
    assertEquals(1, result.size());
    assertEquals(BigDecimal.valueOf(100), result.get(0).getAmount());
  }

  @Test
  void getFundBalances() {
    // given
    setupServiceTokenGeneration();
    LocalDate requestDate = LocalDate.of(2024, 5, 7);
    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis")
            .pathSegment("fund-balances")
            .queryParam("requestDate", requestDate.toString())
            .toUriString();

    TransactionFundBalanceDto[] mockResponseArray = {
      TransactionFundBalanceDto.builder().fundManager("ManagerA").isin("ISIN001").build(),
      TransactionFundBalanceDto.builder().fundManager("ManagerB").isin("ISIN002").build()
    };
    ResponseEntity<TransactionFundBalanceDto[]> mockResponseEntity =
        new ResponseEntity<>(mockResponseArray, OK);

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(TransactionFundBalanceDto[].class)))
        .thenReturn(mockResponseEntity);

    // when
    List<TransactionFundBalanceDto> actualFundBalances = service.getFundBalances(requestDate);

    // then
    assertNotNull(actualFundBalances);
    assertEquals(2, actualFundBalances.size());
    assertEquals("ManagerA", actualFundBalances.get(0).getFundManager());
    assertEquals("ISIN001", actualFundBalances.get(0).getIsin());
    assertEquals("ManagerB", actualFundBalances.get(1).getFundManager());
    assertEquals("ISIN002", actualFundBalances.get(1).getIsin());

    verify(jwtTokenUtil).generateServiceToken();
    verify(restTemplate)
        .exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(TransactionFundBalanceDto[].class));
  }

  @Test
  void getUnitOwners() {
    // given
    setupServiceTokenGeneration();
    String expectedUrl =
        UriComponentsBuilder.fromHttpUrl("http://epis").pathSegment("unit-owners").toUriString();

    UnitOwnerDto[] mockResponseArray = {
      UnitOwnerDto.builder().personId("38001010000").name("OwnerA").build(),
      UnitOwnerDto.builder().personId("49002020000").name("OwnerB").build()
    };
    ResponseEntity<UnitOwnerDto[]> mockResponseEntity = new ResponseEntity<>(mockResponseArray, OK);

    when(restTemplate.exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(UnitOwnerDto[].class)))
        .thenReturn(mockResponseEntity);

    // when
    List<UnitOwnerDto> actualUnitOwners = service.getUnitOwners();

    // then
    assertNotNull(actualUnitOwners);
    assertEquals(2, actualUnitOwners.size());
    assertEquals("38001010000", actualUnitOwners.get(0).getPersonId());
    assertEquals("OwnerA", actualUnitOwners.get(0).getName());
    assertEquals("49002020000", actualUnitOwners.get(1).getPersonId());
    assertEquals("OwnerB", actualUnitOwners.get(1).getName());

    verify(jwtTokenUtil).generateServiceToken();
    verify(restTemplate)
        .exchange(
            eq(expectedUrl),
            eq(GET),
            argThat(entity -> doesHttpEntityContainToken(entity, sampleServiceToken)),
            eq(UnitOwnerDto[].class));
  }

  @Test
  void clearCache() {
    assertDoesNotThrow(
        () -> service.clearCache(samplePerson), "clearCache should not throw an exception");
  }

  @Test
  void noAuthenticationPresent_throwsException() {
    // given
    SecurityContextHolder.clearContext();

    // when
    Exception exception =
        assertThrows(IllegalStateException.class, () -> service.getContactDetails(samplePerson));

    // then
    assertEquals("No authentication present!", exception.getMessage());
  }

  private boolean doesHttpEntityContainToken(HttpEntity<?> entity, String expectedToken) {
    if (entity == null || entity.getHeaders() == null) {
      return false;
    }
    HttpHeaders headers = entity.getHeaders();
    String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
    return ("Bearer " + expectedToken).equals(authHeader);
  }
}
