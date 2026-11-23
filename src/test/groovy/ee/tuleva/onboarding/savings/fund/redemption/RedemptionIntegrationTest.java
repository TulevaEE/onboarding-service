package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.notification.email.EmailType.PREVIOUS_CONTACT_ACTIVITY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SANCTION;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.time.ZoneOffset.UTC;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief;
import ee.tuleva.onboarding.banking.payment.PaymentApprovalBriefService;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyParty;
import ee.tuleva.onboarding.company.CompanyPartyRepository;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.FundNavProvider;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPayment.Status;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = "admin.api-token=it-token")
class RedemptionIntegrationTest {

  // Monday 2025-09-29 17:00 EET (15:00 UTC) - after 16:00 cutoff
  private static final Instant NOW = Instant.parse("2025-09-29T15:00:00Z");
  private static final String VALID_IBAN = "EE471000001020145685";
  private static final String NEW_EMAIL = "redemption-new-address@example.com";
  private static final String PERSONAL_CODE = "39901019992";
  private static final String COMPANY_REGISTRY_CODE = "16007788";
  private static final String COMPANY_NAME = "Mesila OÜ";
  private static final List<String> PARTY_CODES = List.of(PERSONAL_CODE, COMPANY_REGISTRY_CODE);
  private static final Locale ESTONIAN = Locale.of("et");

  @Autowired RedemptionService redemptionService;
  @Autowired RedemptionStatusService redemptionStatusService;
  @Autowired RedemptionVerificationService redemptionVerificationService;
  @Autowired RedemptionRequestRepository redemptionRequestRepository;
  @Autowired RedemptionBatchJob redemptionBatchJob;
  @Autowired RedemptionHoldService redemptionHoldService;
  @Autowired PaymentApprovalBriefService paymentApprovalBriefService;
  @Autowired RedemptionPayoutRecorder redemptionPayoutRecorder;
  @Autowired EndToEndIdConverter endToEndIdConverter;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired LedgerService ledgerService;
  @Autowired UserRepository userRepository;
  @Autowired SavingFundPaymentRepository savingFundPaymentRepository;
  @Autowired MockMvc mockMvc;
  @Autowired CompanyRepository companyRepository;
  @Autowired CompanyPartyRepository companyPartyRepository;
  @Autowired ApplicationEvents applicationEvents;
  @Autowired UserService userService;
  @Autowired EmailPersistenceService emailPersistenceService;
  @Autowired JdbcClient jdbcClient;

  @MockitoSpyBean EmailService emailService;
  @MockitoBean SebGatewayClient sebGatewayClient;
  @MockitoBean OperationsNotificationService operationsNotificationService;
  @MockitoBean FundNavProvider navProvider;
  @MockitoBean RiskLevels riskLevels;

  User testUser;
  boolean testDataCommitted;
  PartyId testParty;
  AuthenticatedPerson testAuthenticatedPerson;

  @BeforeEach
  void setUp() {
    deleteWhatCommittedTestsLeft();
    ClockHolder.setClock(Clock.fixed(NOW, UTC));
    LocaleContextHolder.setLocale(ENGLISH);
    keepEveryEmailAwayFromMandrill();
    doReturn("").when(sebGatewayClient).submitPaymentFile(any(), any(), any());
    lenient().when(navProvider.getDisplayNav(any())).thenReturn(BigDecimal.ONE);
    lenient()
        .when(navProvider.getVerifiedNavForIssuingAndRedeeming(any(), any()))
        .thenReturn(BigDecimal.ONE);

    testUser =
        userRepository.save(sampleUser().id(null).member(null).personalCode(PERSONAL_CODE).build());
    testParty = new PartyId(PartyId.Type.PERSON, testUser.getPersonalCode());
    testAuthenticatedPerson = authenticatedPersonFromUser(testUser).build();
    savingsFundOnboardingRepository.saveOnboardingStatus(
        testUser.getPersonalCode(), PartyId.Type.PERSON, COMPLETED, KYC_CHECK);
    setupUserWithFundUnits(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    setupUserDepositIban(VALID_IBAN);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
    LocaleContextHolder.resetLocaleContext();
    if (testDataCommitted) {
      rollBackATestTransactionLeftOpen();
      deleteWhatCommittedTestsLeft();
    }
  }

  @Test
  void createRedemptionRequest_createsRequestInReservedStatus() {
    var amount = new BigDecimal("10.00");

    var request =
        redemptionService.createRedemptionRequest(testAuthenticatedPerson, amount, EUR, VALID_IBAN);

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(request.getFundUnits()).isEqualByComparingTo(new BigDecimal("10.00000"));
    assertThat(request.getRequestedAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    assertThat(request.getCustomerIban()).isEqualTo(VALID_IBAN);
    assertThat(request.getUserId()).isEqualTo(testUser.getId());
    assertThat(request.getPartyType()).isEqualTo(PartyId.Type.PERSON);
    assertThat(request.getPartyCode()).isEqualTo(testUser.getPersonalCode());
    assertThat(request.getPartyId()).isEqualTo(testParty);
    assertThat(request.getRequestedAt()).isNotNull();
  }

  @Test
  void createRedemptionRequest_failsWhenInsufficientUnits() {
    var amount = new BigDecimal("200.00");

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testAuthenticatedPerson, amount, EUR, VALID_IBAN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRedemptionRequest_maxWithdrawalRoundsToExactBalance() {
    var maxAmount = new BigDecimal("100.00");

    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, maxAmount, EUR, VALID_IBAN);

    assertThat(request.getFundUnits()).isEqualByComparingTo(new BigDecimal("100.00000"));
  }

  @Test
  void createRedemptionRequest_failsWhenAmountHasMoreThanTwoDecimals() {
    var amount = new BigDecimal("10.123");

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testAuthenticatedPerson, amount, EUR, VALID_IBAN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancelRedemption_cancelsRequestBeforeCutoff() {
    var amount = new BigDecimal("10.00");
    var request =
        redemptionService.createRedemptionRequest(testAuthenticatedPerson, amount, EUR, VALID_IBAN);

    redemptionService.cancelRedemption(request.getId(), testAuthenticatedPerson);

    var cancelled = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(CANCELLED);
    assertThat(cancelled.getCancelledAt()).isNotNull();
  }

  @Test
  void cancelRedemption_calledTwice_isIdempotent() {
    var amount = new BigDecimal("10.00");
    var request =
        redemptionService.createRedemptionRequest(testAuthenticatedPerson, amount, EUR, VALID_IBAN);

    redemptionService.cancelRedemption(request.getId(), testAuthenticatedPerson);
    redemptionService.cancelRedemption(request.getId(), testAuthenticatedPerson);

    var cancelled = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(CANCELLED);
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  void cancelRedemption_afterDeadline_throwsException() {
    // Monday 2025-09-29 17:00 EET (15:00 UTC) - after 16:00 cutoff
    // Deadline for cancellation: Tuesday 2025-09-30 16:00 EET
    var monday = Instant.parse("2025-09-29T15:00:00Z");
    ClockHolder.setClock(Clock.fixed(monday, ZoneId.of("Europe/Tallinn")));

    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, new BigDecimal("10.00"), EUR, VALID_IBAN);

    var tuesdayAfterDeadline = Instant.parse("2025-09-30T14:01:00Z");
    ClockHolder.setClock(Clock.fixed(tuesdayAfterDeadline, ZoneId.of("Europe/Tallinn")));

    assertThatThrownBy(
            () -> redemptionService.cancelRedemption(request.getId(), testAuthenticatedPerson))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cancellation deadline has passed");
  }

  @Test
  void getPartyRedemptions_returnsPartyRedemptions() {
    redemptionService.createRedemptionRequest(
        testAuthenticatedPerson, new BigDecimal("5.00"), EUR, VALID_IBAN);
    redemptionService.createRedemptionRequest(
        testAuthenticatedPerson, new BigDecimal("3.00"), EUR, VALID_IBAN);

    var redemptions = redemptionService.getPendingRedemptionsForParty(testParty);

    assertThat(redemptions).hasSize(2);
  }

  @Test
  void createRedemptionRequest_locksUnitsInLedger() {
    var amount = new BigDecimal("10.00");

    var request =
        redemptionService.createRedemptionRequest(testAuthenticatedPerson, amount, EUR, VALID_IBAN);

    assertThat(request.getStatus()).isEqualTo(RESERVED);

    var reservedUnitsBalance = getUserFundUnitsReservedAccount().getBalance();
    assertThat(reservedUnitsBalance).isEqualByComparingTo(new BigDecimal("-10.00000"));
  }

  @Test
  void fullRedemptionFlow_allBalancesCorrect() {
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("10.00");
    var cashAmount = new BigDecimal("100.00");

    // Step 1: Reserve units (happens when redemption request created)
    var redemptionRequestId = UUID.randomUUID();
    var testPartyRef = LedgerRefs.from(testParty);
    savingsFundLedger.reserveFundUnitsForRedemption(testPartyRef, fundUnits, redemptionRequestId);

    // Step 2: Price and redeem (happens in batch job at T+2)
    savingsFundLedger.redeemFundUnitsFromReserved(
        testPartyRef,
        fundUnits,
        cashAmount,
        navPerUnit,
        LocalDate.parse("2025-03-10"),
        redemptionRequestId);

    // Steps 3 & 4: Transfer and payout (happens during bank statement reconciliation)
    savingsFundLedger.transferFromFundAccount(cashAmount, redemptionRequestId);
    savingsFundLedger.recordRedemptionPayout(
        testPartyRef, cashAmount, VALID_IBAN, redemptionRequestId);

    var fundsUnitsBalance = getUserFundUnitsAccount().getBalance();
    var reservedUnitsBalance = getUserFundUnitsReservedAccount().getBalance();
    var redemptionsBalance = getUserRedemptionsAccount().getBalance();

    assertThat(fundsUnitsBalance.negate()).isEqualByComparingTo(new BigDecimal("90.00000"));
    assertThat(reservedUnitsBalance).isEqualByComparingTo(ZERO);
    assertThat(redemptionsBalance).isEqualByComparingTo(cashAmount);
  }

  @Test
  void endToEndBatchJob_processesRedemptionRequest() {
    var initialFundUnits = new BigDecimal("100.00000");
    var redemptionAmount = new BigDecimal("25.00");
    var expectedFundUnits = new BigDecimal("25.00000");
    var remainingUnits = initialFundUnits.subtract(expectedFundUnits);

    // Friday 17:00 EET (14:00 UTC) - AFTER 16:00 EET cutoff
    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    // Step 1: Create redemption request on Friday (after cutoff) - immediately RESERVED
    ClockHolder.setClock(Clock.fixed(friday, UTC));
    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(remainingUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance())
        .isEqualByComparingTo(expectedFundUnits.negate());

    // Step 2: Simulate verification job changing status to VERIFIED
    redemptionStatusService.changeStatus(requestId, VERIFIED);

    var afterVerification = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterVerification.getStatus()).isEqualTo(VERIFIED);

    // Step 3: Run batch job on Tuesday - prices units and sends payments to bank
    // Note: Ledger entries for transfer (step 3) and payout (step 4) happen during reconciliation
    ClockHolder.setClock(Clock.fixed(tuesday, UTC));
    redemptionBatchJob.runJob();

    var afterBatchJob = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterBatchJob.getStatus()).isEqualTo(REDEEMED);
    assertThat(afterBatchJob.getCashAmount()).isEqualByComparingTo(redemptionAmount);
    assertThat(afterBatchJob.getNavPerUnit()).isNotNull();
    assertThat(afterBatchJob.getProcessedAt()).isNotNull();

    // Fund units redeemed from reserved
    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(remainingUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(savingsFundLedger.hasPricingEntry(requestId)).isTrue();

    // Cash redemption account has pending payout (will be cleared during reconciliation)
    assertThat(getUserCashRedemptionAccount().getBalance())
        .isEqualByComparingTo(redemptionAmount.negate());
  }

  @Test
  void cancelledRedemption_notProcessedByBatchJob() {
    var initialFundUnits = new BigDecimal("100.00000");
    var redemptionAmount = new BigDecimal("50.00");
    var reservedFundUnits = new BigDecimal("50.00000");

    var friday = Instant.parse("2025-09-26T10:00:00Z");
    var monday = Instant.parse("2025-09-29T15:00:00Z");

    // Create redemption on Friday - immediately RESERVED
    ClockHolder.setClock(Clock.fixed(friday, UTC));
    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(getUserFundUnitsReservedAccount().getBalance())
        .isEqualByComparingTo(reservedFundUnits.negate());

    // Cancel before batch job runs - should release reserved units
    redemptionService.cancelRedemption(requestId, testAuthenticatedPerson);

    // Run batch job on Monday
    ClockHolder.setClock(Clock.fixed(monday, UTC));
    redemptionBatchJob.runJob();

    // Should remain cancelled
    var afterBatch = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterBatch.getStatus()).isEqualTo(CANCELLED);

    // Fund units returned to user account
    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(initialFundUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  private void setupUserWithFundUnits(BigDecimal cashAmount, BigDecimal fundUnits) {
    var navPerUnit = cashAmount.divide(fundUnits, 5, HALF_UP);
    var paymentId = UUID.randomUUID();
    var testPartyRef = LedgerRefs.from(testParty);
    savingsFundLedger.recordPaymentReceived(testPartyRef, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(testPartyRef, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        testPartyRef, cashAmount, fundUnits, navPerUnit, LocalDate.parse("2025-03-10"), paymentId);
    savingsFundLedger.transferToFundAccount(cashAmount, paymentId);
  }

  private LedgerAccount getUserFundUnitsAccount() {
    return ledgerService.getPartyAccount(testUser.getPersonalCode(), PERSON, FUND_UNITS);
  }

  private LedgerAccount getUserFundUnitsReservedAccount() {
    return ledgerService.getPartyAccount(testUser.getPersonalCode(), PERSON, FUND_UNITS_RESERVED);
  }

  private LedgerAccount getUserCashRedemptionAccount() {
    return ledgerService.getPartyAccount(testUser.getPersonalCode(), PERSON, CASH_REDEMPTION);
  }

  private LedgerAccount getUserRedemptionsAccount() {
    return ledgerService.getPartyAccount(testUser.getPersonalCode(), PERSON, REDEMPTIONS);
  }

  private void setupUserDepositIban(String iban) {
    setupUserDepositIban(testUser, iban);
  }

  private void setupUserDepositIban(User user, String iban) {
    var payment =
        SavingFundPayment.builder()
            .externalId("TEST-" + UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .description("Test deposit")
            .remitterIban(iban)
            .remitterIdCode(user.getPersonalCode())
            .remitterName(user.getFullName())
            .beneficiaryIban("EE362200221234567897")
            .beneficiaryIdCode("12345678")
            .beneficiaryName("Tuleva")
            .status(Status.PROCESSED)
            .build();

    var paymentId = savingFundPaymentRepository.savePaymentData(payment);
    savingFundPaymentRepository.attachParty(
        paymentId, new PartyId(PartyId.Type.PERSON, user.getPersonalCode()));
    savingFundPaymentRepository.changeStatus(paymentId, Status.RECEIVED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.VERIFIED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.RESERVED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.ISSUED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.PROCESSED);
  }

  @Test
  void createRedemptionRequest_failsWhenIbanDoesNotBelongToParty() {
    var amount = new BigDecimal("10.00");
    var invalidIban = "EE382200221020145685";

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testAuthenticatedPerson, amount, EUR, invalidIban))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRedemptionRequest_succeedsForWhitelistedIban() throws Exception {
    var whitelistedIban = "EE382200221020145685";
    whitelistIbanViaAdminEndpoint(whitelistedIban);

    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, new BigDecimal("10.00"), EUR, whitelistedIban);

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(request.getCustomerIban()).isEqualTo(whitelistedIban);
  }

  @Test
  void createRedemptionRequest_canonicalizesCustomerIbanBeforeStoring() throws Exception {
    var canonicalIban = "EE382200221020145685";
    var messyIban = "ee38 2200 2210 2014 5685";
    whitelistIbanViaAdminEndpoint(messyIban);

    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, new BigDecimal("10.00"), EUR, messyIban);

    assertThat(request.getCustomerIban()).isEqualTo(canonicalIban);
  }

  private void whitelistIbanViaAdminEndpoint(String iban) throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", testParty.type().name())
                .param("partyCode", testParty.code())
                .param("iban", iban)
                .param("comment", "verified via bank statement"))
        .andExpect(status().isOk());
  }

  @Test
  void createRedemptionRequest_maxWithdrawalWithPreciseNavRoundsToExactBalance() {
    var preciseNav = new BigDecimal("1.23456");
    var availableFundUnits = new BigDecimal("100.00000");
    var maxWithdrawalValue = availableFundUnits.multiply(preciseNav).setScale(2, HALF_UP);

    when(navProvider.getDisplayNav(any())).thenReturn(preciseNav);

    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, maxWithdrawalValue, EUR, VALID_IBAN);

    assertThat(request.getFundUnits()).isEqualByComparingTo(availableFundUnits);
    assertThat(request.getRequestedAmount()).isEqualByComparingTo(maxWithdrawalValue);
  }

  @Test
  void verifyLegalEntityRedemption_routesToVerifiedWhenLatestKybCompleted() {
    var registryCode = "16001234";
    var companyName = "Acme Holding OÜ";
    var companyIban = "EE382200221020145685";
    var redemptionAmount = new BigDecimal("25.00");

    var leParty = setUpLegalEntity(registryCode, companyName, companyIban);
    var leAuthenticatedPerson =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();

    var request =
        redemptionService.createRedemptionRequest(
            leAuthenticatedPerson, redemptionAmount, EUR, companyIban);
    var requestId = request.getId();

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(request.getPartyId()).isEqualTo(leParty);
    assertThat(request.getPartyType()).isEqualTo(PartyId.Type.LEGAL_ENTITY);
    assertThat(request.getPartyCode()).isEqualTo(registryCode);

    redemptionVerificationService.process(request);

    var afterVerification = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterVerification.getStatus()).isEqualTo(VERIFIED);
  }

  @Test
  void batchJob_paysOutManuallyVerifiedLegalEntityRedemption() {
    var registryCode = "16001234";
    var companyName = "Acme Holding OÜ";
    var companyIban = "EE382200221020145685";
    var redemptionAmount = new BigDecimal("25.00");

    setUpLegalEntity(registryCode, companyName, companyIban);
    var leAuthenticatedPerson =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();

    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    ClockHolder.setClock(Clock.fixed(friday, UTC));
    var request =
        redemptionService.createRedemptionRequest(
            leAuthenticatedPerson, redemptionAmount, EUR, companyIban);
    var requestId = request.getId();

    redemptionStatusService.changeStatus(requestId, VERIFIED);

    ClockHolder.setClock(Clock.fixed(tuesday, UTC));
    redemptionBatchJob.runJob();

    var afterBatch = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterBatch.getStatus()).isEqualTo(REDEEMED);
    assertThat(afterBatch.getCashAmount()).isEqualByComparingTo(redemptionAmount);
    assertThat(afterBatch.getProcessedAt()).isNotNull();

    var payoutEvent =
        applicationEvents.stream(RequestPaymentEvent.class)
            .filter(e -> requestId.equals(e.sourceId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No RequestPaymentEvent for redemption request"));
    assertThat(payoutEvent.paymentRequest().beneficiaryName()).isEqualTo(companyName);
    assertThat(payoutEvent.paymentRequest().beneficiaryIban()).isEqualTo(companyIban);
    assertThat(payoutEvent.paymentRequest().amount()).isEqualByComparingTo(redemptionAmount);
  }

  @Test
  void bankPayout_ofLegalEntityRedemption_settlesTheCompanyAccountNotTheBoardMembers() {
    var registryCode = "16001234";
    var companyName = "Acme Holding OÜ";
    var companyIban = "EE442200221092874625";
    var redemptionAmount = new BigDecimal("25.00");
    setUpLegalEntity(registryCode, companyName, companyIban);
    var leAuthenticatedPerson =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();
    ClockHolder.setClock(Clock.fixed(Instant.parse("2025-09-26T14:00:00Z"), UTC));
    var requestId =
        redemptionService
            .createRedemptionRequest(leAuthenticatedPerson, redemptionAmount, EUR, companyIban)
            .getId();
    redemptionStatusService.changeStatus(requestId, VERIFIED);
    ClockHolder.setClock(Clock.fixed(Instant.parse("2025-09-30T15:00:00Z"), UTC));
    redemptionBatchJob.runJob();
    var companyCashRedemption =
        ledgerService.getPartyAccount(
            registryCode, LedgerParty.PartyType.LEGAL_ENTITY, CASH_REDEMPTION);
    assertThat(companyCashRedemption.getBalance()).isEqualByComparingTo(redemptionAmount.negate());

    redemptionPayoutRecorder.recordOutgoingPayout(
        aPayment()
            .amount(redemptionAmount.negate())
            .beneficiaryIban(companyIban)
            .endToEndId(endToEndIdConverter.toEndToEndId(requestId))
            .receivedBefore(Instant.parse("2025-09-30T15:30:00Z"))
            .build());

    assertThat(companyCashRedemption.getBalance()).isEqualByComparingTo(ZERO);
    assertThat(
            ledgerService
                .getPartyAccount(testUser.getPersonalCode(), PERSON, CASH_REDEMPTION)
                .getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(redemptionRequestRepository.findById(requestId).orElseThrow().getStatus())
        .isEqualTo(PROCESSED);
  }

  @Test
  void createRedemption_legalEntityNonBoardMember_denied() {
    var registryCode = "16002345";
    var companyName = "Other OÜ";
    var companyIban = "EE382200221020145685";

    // Set up an LE that is whitelisted/onboarded but the testUser is NOT a board member
    var company =
        companyRepository.save(
            Company.builder().registryCode(registryCode).name(companyName).build());
    // Different person is the board member, not testUser
    companyPartyRepository.save(
        CompanyParty.builder()
            .partyCode("99999999999")
            .partyType(PartyId.Type.PERSON)
            .companyId(company.getId())
            .relationshipType(BOARD_MEMBER)
            .build());
    savingsFundOnboardingRepository.saveOnboardingStatus(
        registryCode, PartyId.Type.LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    setupPartyDepositIban(
        new PartyId(PartyId.Type.LEGAL_ENTITY, registryCode),
        companyIban,
        companyName,
        registryCode);
    setupPartyWithFundUnits(
        new PartyId(PartyId.Type.LEGAL_ENTITY, registryCode),
        new BigDecimal("1000.00"),
        new BigDecimal("100.00000"));

    var leAuthenticatedPerson =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    leAuthenticatedPerson, new BigDecimal("10.00"), EUR, companyIban))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void cancelRedemption_byOtherBoardMemberOfSameCompany_succeeds() {
    var registryCode = "16003456";
    var companyName = "Twoboard OÜ";
    var companyIban = "EE471000001020145685";

    var company =
        companyRepository.save(
            Company.builder().registryCode(registryCode).name(companyName).build());
    // testUser is board member (creator)
    companyPartyRepository.save(
        CompanyParty.builder()
            .partyCode(testUser.getPersonalCode())
            .partyType(PartyId.Type.PERSON)
            .companyId(company.getId())
            .relationshipType(BOARD_MEMBER)
            .build());

    // Second board member of the same company
    var otherBoardMember =
        userRepository.save(
            sampleUser()
                .id(null)
                .member(null)
                .personalCode("38001085718")
                .email("other.boardmember@example.com")
                .build());
    companyPartyRepository.save(
        CompanyParty.builder()
            .partyCode(otherBoardMember.getPersonalCode())
            .partyType(PartyId.Type.PERSON)
            .companyId(company.getId())
            .relationshipType(BOARD_MEMBER)
            .build());

    savingsFundOnboardingRepository.saveOnboardingStatus(
        registryCode, PartyId.Type.LEGAL_ENTITY, COMPLETED, KYB_CHECK);
    var leParty = new PartyId(PartyId.Type.LEGAL_ENTITY, registryCode);
    setupPartyDepositIban(leParty, companyIban, companyName, registryCode);
    setupPartyWithFundUnits(leParty, new BigDecimal("1000.00"), new BigDecimal("100.00000"));

    var creatorAuthPerson =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();
    var otherBoardMemberAuthPerson =
        authenticatedPersonFromUser(otherBoardMember)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();

    var request =
        redemptionService.createRedemptionRequest(
            creatorAuthPerson, new BigDecimal("10.00"), EUR, companyIban);

    redemptionService.cancelRedemption(request.getId(), otherBoardMemberAuthPerson);

    var cancelled = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(CANCELLED);
  }

  @Test
  void cancelRedemption_legalEntityByUnrelatedPerson_denied() {
    var registryCode = "16004567";
    var companyName = "Solo OÜ";
    var companyIban = "EE471000001020145686";

    var leParty = setUpLegalEntity(registryCode, companyName, companyIban);
    var creatorAuthPerson =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, registryCode, companyName))
            .build();
    var request =
        redemptionService.createRedemptionRequest(
            creatorAuthPerson, new BigDecimal("10.00"), EUR, companyIban);

    var unrelatedUser =
        userRepository.save(
            sampleUser()
                .id(null)
                .member(null)
                .personalCode("39907052754")
                .email("unrelated@example.com")
                .build());
    var unrelatedAuthPerson = authenticatedPersonFromUser(unrelatedUser).build();

    assertThatThrownBy(
            () -> redemptionService.cancelRedemption(request.getId(), unrelatedAuthPerson))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    assertThat(leParty.type()).isEqualTo(PartyId.Type.LEGAL_ENTITY);
  }

  private PartyId setUpLegalEntity(String registryCode, String companyName, String companyIban) {
    var company =
        companyRepository.save(
            Company.builder().registryCode(registryCode).name(companyName).build());
    companyPartyRepository.save(
        CompanyParty.builder()
            .partyCode(testUser.getPersonalCode())
            .partyType(PartyId.Type.PERSON)
            .companyId(company.getId())
            .relationshipType(BOARD_MEMBER)
            .build());
    savingsFundOnboardingRepository.saveOnboardingStatus(
        registryCode, PartyId.Type.LEGAL_ENTITY, COMPLETED, KYB_CHECK);

    var leParty = new PartyId(PartyId.Type.LEGAL_ENTITY, registryCode);
    setupPartyDepositIban(leParty, companyIban, companyName, registryCode);
    setupPartyWithFundUnits(leParty, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    return leParty;
  }

  private void setupPartyDepositIban(
      PartyId party, String iban, String remitterName, String remitterIdCode) {
    var payment =
        SavingFundPayment.builder()
            .externalId("TEST-" + UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .description("Test LE deposit")
            .remitterIban(iban)
            .remitterIdCode(remitterIdCode)
            .remitterName(remitterName)
            .beneficiaryIban("EE362200221234567897")
            .beneficiaryIdCode("12345678")
            .beneficiaryName("Tuleva")
            .status(Status.PROCESSED)
            .build();

    var paymentId = savingFundPaymentRepository.savePaymentData(payment);
    savingFundPaymentRepository.attachParty(paymentId, party);
    savingFundPaymentRepository.changeStatus(paymentId, Status.RECEIVED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.VERIFIED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.RESERVED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.ISSUED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.PROCESSED);
  }

  private void setupPartyWithFundUnits(PartyId party, BigDecimal cashAmount, BigDecimal fundUnits) {
    var navPerUnit = cashAmount.divide(fundUnits, 5, HALF_UP);
    var paymentId = UUID.randomUUID();
    var partyRef = LedgerRefs.from(party);
    savingsFundLedger.recordPaymentReceived(partyRef, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(partyRef, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        partyRef, cashAmount, fundUnits, navPerUnit, LocalDate.parse("2025-03-10"), paymentId);
    savingsFundLedger.transferToFundAccount(cashAmount, paymentId);
  }

  @Test
  void amlSuspicion_redeemsTheUnitsButHoldsTheCashUntilAPersonReleasesIt() {
    var redemptionAmount = new BigDecimal("25.00");
    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    ClockHolder.setClock(Clock.fixed(friday, UTC));
    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    redemptionHoldService.holdPayout(requestId, Set.of(PEP));
    redemptionStatusService.changeStatus(requestId, VERIFIED);

    ClockHolder.setClock(Clock.fixed(tuesday, UTC));
    redemptionBatchJob.runJob();

    var held = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(held.getStatus()).isEqualTo(PAYOUT_HELD);
    assertThat(held.getCashAmount()).isEqualByComparingTo(redemptionAmount);
    verify(operationsNotificationService)
        .sendMessage(
            contains("payout held for review: id=%s, cashAmount=25.00 EUR".formatted(requestId)),
            eq(AML));
    assertThat(held.getProcessedAt()).isNull();
    assertThat(savingsFundLedger.hasPricingEntry(requestId)).isTrue();
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashRedemptionAccount().getBalance())
        .isEqualByComparingTo(redemptionAmount.negate());
    assertThat(
            applicationEvents.stream(RequestPaymentEvent.class)
                .filter(e -> requestId.equals(e.sourceId())))
        .isEmpty();
    verify(sebGatewayClient, times(1)).submitPaymentFile(any(), any(), any());
    var fundingBatch = held.getBatchId();
    assertThat(fundingBatch).isNotNull();
    assertThat(briefVerdictsOn(tuesday))
        .contains(
            batchTie(
                "payouts + held == transfer to withdrawal account",
                fundingBatch,
                "0.00 + 25.00 held = 25.00"));

    redemptionHoldService.release(requestId, "AML Specialist", "Source of funds confirmed");

    var released = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(released.getStatus()).isEqualTo(REDEEMED);
    assertThat(released.getReviewedBy()).isEqualTo("AML Specialist");
    assertThat(released.getReviewReason()).isEqualTo("Source of funds confirmed");
    assertThat(released.getProcessedAt()).isNotNull();
    var payoutEvent =
        applicationEvents.stream(RequestPaymentEvent.class)
            .filter(e -> requestId.equals(e.sourceId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No payout event after release"));
    assertThat(payoutEvent.paymentRequest().amount()).isEqualByComparingTo(redemptionAmount);
    assertThat(payoutEvent.paymentRequest().beneficiaryIban()).isEqualTo(VALID_IBAN);
    verify(sebGatewayClient, times(2)).submitPaymentFile(any(), any(), any());
    assertThat(payoutEvent.batchId()).isEqualTo(fundingBatch);
    assertThat(briefVerdictsOn(tuesday))
        .contains(
            batchTie("payouts == transfer to withdrawal account", fundingBatch, "25.00 = 25.00"));
  }

  private List<PaymentApprovalBrief.Verdict> briefVerdictsOn(Instant instant) {
    return paymentApprovalBriefService
        .build(LocalDate.ofInstant(instant, ZoneId.of("Europe/Tallinn")), List.of())
        .verdicts();
  }

  private static PaymentApprovalBrief.Verdict batchTie(String label, UUID batchId, String detail) {
    return new PaymentApprovalBrief.Verdict(
        "%s (batch %s)".formatted(label, batchId.toString().substring(0, 8)), true, detail);
  }

  @Test
  void sanctionsHit_freezesTheOrderUntilAPersonReleasesIt() {
    var redemptionAmount = new BigDecimal("25.00");
    var expectedFundUnits = new BigDecimal("25.00000");
    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");
    var thursday = Instant.parse("2025-10-02T15:00:00Z");

    ClockHolder.setClock(Clock.fixed(friday, UTC));
    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    redemptionHoldService.freeze(requestId);

    var frozen = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(frozen.getStatus()).isEqualTo(FROZEN);
    assertThat(frozen.getHoldReasons()).containsExactly(SANCTION);

    redemptionService.cancelRedemption(requestId, testAuthenticatedPerson);
    assertThat(redemptionRequestRepository.findById(requestId).orElseThrow().getStatus())
        .isEqualTo(FROZEN);
    assertThat(getUserFundUnitsReservedAccount().getBalance())
        .isEqualByComparingTo(expectedFundUnits.negate());

    ClockHolder.setClock(Clock.fixed(tuesday, UTC));
    redemptionBatchJob.runJob();
    assertThat(redemptionRequestRepository.findById(requestId).orElseThrow().getStatus())
        .isEqualTo(FROZEN);
    assertThat(savingsFundLedger.hasPricingEntry(requestId)).isFalse();
    verify(sebGatewayClient, never()).submitPaymentFile(any(), any(), any());

    redemptionHoldService.release(requestId, "Contact person", "False positive, namesake");

    var released = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(released.getStatus()).isEqualTo(VERIFIED);
    assertThat(released.getReviewedBy()).isEqualTo("Contact person");
    assertThat(released.hasActiveHold()).isFalse();

    ClockHolder.setClock(Clock.fixed(thursday, UTC));
    redemptionBatchJob.runJob();

    var paidOut = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(paidOut.getStatus()).isEqualTo(REDEEMED);
    assertThat(paidOut.getCashAmount()).isEqualByComparingTo(redemptionAmount);
    verify(sebGatewayClient, times(2)).submitPaymentFile(any(), any(), any());
  }

  @Test
  void batchJobIdempotency_runningTwiceDoesNotCreateDuplicates() {
    var redemptionAmount = new BigDecimal("25.00");

    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    ClockHolder.setClock(Clock.fixed(friday, UTC));
    var request =
        redemptionService.createRedemptionRequest(
            testAuthenticatedPerson, redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    redemptionStatusService.changeStatus(requestId, VERIFIED);

    ClockHolder.setClock(Clock.fixed(tuesday, UTC));

    redemptionBatchJob.runJob();
    var afterFirstRun = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterFirstRun.getStatus()).isEqualTo(REDEEMED);

    var cashRedemptionAfterFirst = getUserCashRedemptionAccount().getBalance();

    redemptionBatchJob.runJob();
    var afterSecondRun = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterSecondRun.getStatus()).isEqualTo(REDEEMED);

    var cashRedemptionAfterSecond = getUserCashRedemptionAccount().getBalance();

    // Ledger balances should not change on second run
    assertThat(cashRedemptionAfterSecond).isEqualByComparingTo(cashRedemptionAfterFirst);

    // Payments sent only once (batch + individual)
    verify(sebGatewayClient, times(2)).submitPaymentFile(any(), any(), any());
  }

  @Test
  void aRedemptionRequestIsConfirmedAtTheCurrentAddressWithTheMaskedIbanAndDeadlinesAndRecorded() {
    var currentEmail = testUser.getEmail();
    willReturn(Optional.of(acceptedFor(currentEmail)))
        .given(emailService)
        .send(any(), argThat(addressedTo(currentEmail)), eq("savings_fund_redemption_person_en"));

    redemptionService.createRedemptionRequest(
        testAuthenticatedPerson, new BigDecimal("10.00"), EUR, VALID_IBAN);
    commitTheTestTransaction();

    then(emailService)
        .should()
        .newMandrillMessage(
            eq(currentEmail),
            eq("savings_fund_redemption_person_en"),
            eq(
                Map.of(
                    "fname", "Jordan",
                    "amount", "10.00",
                    "fundUnits", "10.00000",
                    "iban", "EE47 **** **** **** 5685",
                    "cancellationDeadline", "30.09.2025 16:00",
                    "payoutDeadline", "01.10.2025 16:00")),
            any());
    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(testUser.getPersonalCode())),
            argThat(addressedTo(currentEmail)),
            eq("savings_fund_redemption_person_en"));
    assertThat(
            emailPersistenceService.hasPendingOrSentEmail(testUser, SAVINGS_FUND_REDEMPTION_PERSON))
        .isTrue();
  }

  @Test
  void aRedemptionRequestedWithinTheWindowAfterAnEmailChangeIsNoticedAtTheReplacedAddress() {
    var replacedEmail = testUser.getEmail();
    changeEmailOn(NOW.minus(Duration.ofDays(27)));

    redemptionService.createRedemptionRequest(
        testAuthenticatedPerson, new BigDecimal("10.00"), EUR, VALID_IBAN);
    commitTheTestTransaction();

    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(testUser.getPersonalCode())),
            argThat(addressedTo(replacedEmail)),
            eq("previous_contact_activity_en"));
    then(emailService)
        .should()
        .send(any(), argThat(addressedTo(NEW_EMAIL)), eq("savings_fund_redemption_person_en"));
    then(emailService).should(times(1)).send(any(), any(), eq("previous_contact_activity_en"));
  }

  @Test
  void aRedemptionRequestedAfterTheWindowAfterAnEmailChangeIsOnlyConfirmedAtTheNewAddress() {
    changeEmailOn(NOW.minus(Duration.ofDays(29)));

    redemptionService.createRedemptionRequest(
        testAuthenticatedPerson, new BigDecimal("10.00"), EUR, VALID_IBAN);
    commitTheTestTransaction();

    then(emailService)
        .should()
        .send(any(), argThat(addressedTo(NEW_EMAIL)), eq("savings_fund_redemption_person_en"));
    then(emailService).should(never()).send(any(), any(), eq("previous_contact_activity_en"));
  }

  @Test
  void aRedemptionRequestRolledBackAfterItsEventSendsNeitherTheConfirmationNorTheNotice() {
    changeEmailOn(NOW.minus(Duration.ofDays(27)));
    commitTheTestTransaction();
    TestTransaction.start();

    redemptionService.createRedemptionRequest(
        testAuthenticatedPerson, new BigDecimal("10.00"), EUR, VALID_IBAN);
    assertThat(applicationEvents.stream(RedemptionRequestedEvent.class)).hasSize(1);
    TestTransaction.flagForRollback();
    TestTransaction.end();

    then(emailService).should(never()).send(any(), any(), eq("savings_fund_redemption_person_en"));
    then(emailService).should(never()).send(any(), any(), eq("previous_contact_activity_en"));
  }

  @Test
  void
      aBoardMembersRedemptionAfterAnEmailChangeIsNoticedAtTheReplacedAddressAndConfirmedInEstonian() {
    LocaleContextHolder.setLocale(ESTONIAN);
    when(navProvider.getDisplayNav(any())).thenReturn(new BigDecimal("12.34560"));
    setUpLegalEntity(COMPANY_REGISTRY_CODE, COMPANY_NAME, VALID_IBAN);
    var replacedEmail = testUser.getEmail();
    changeEmailOn(NOW.minus(Duration.ofDays(27)));
    var boardMember =
        authenticatedPersonFromUser(testUser)
            .role(new Role(LEGAL_ENTITY, COMPANY_REGISTRY_CODE, COMPANY_NAME))
            .build();
    willReturn(Optional.of(acceptedFor(NEW_EMAIL)))
        .given(emailService)
        .send(any(), argThat(addressedTo(NEW_EMAIL)), eq("savings_fund_redemption_company_et"));
    willReturn(Optional.of(acceptedFor(replacedEmail)))
        .given(emailService)
        .send(any(), argThat(addressedTo(replacedEmail)), eq("previous_contact_activity_et"));

    redemptionService.createRedemptionRequest(
        boardMember, new BigDecimal("1234.56"), EUR, VALID_IBAN);
    var commitStarted = System.nanoTime();
    commitTheTestTransaction();
    var commitAndListenersTook = Duration.ofNanos(System.nanoTime() - commitStarted);

    then(emailService)
        .should()
        .newMandrillMessage(
            eq(NEW_EMAIL),
            eq("savings_fund_redemption_company_et"),
            eq(
                Map.of(
                    "fname", "Jordan",
                    "amount", "1\u00A0234,56",
                    "fundUnits", "100,00000",
                    "iban", "EE47 **** **** **** 5685",
                    "cancellationDeadline", "30.09.2025 16:00",
                    "payoutDeadline", "01.10.2025 16:00",
                    "recipientName", COMPANY_NAME)),
            any());
    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(PERSONAL_CODE)),
            argThat(addressedTo(NEW_EMAIL)),
            eq("savings_fund_redemption_company_et"));
    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(PERSONAL_CODE)),
            argThat(addressedTo(replacedEmail)),
            eq("previous_contact_activity_et"));
    assertThat(
            emailPersistenceService.hasPendingOrSentEmail(
                testUser, SAVINGS_FUND_REDEMPTION_COMPANY))
        .isTrue();
    assertThat(emailPersistenceService.hasPendingOrSentEmail(testUser, PREVIOUS_CONTACT_ACTIVITY))
        .isTrue();
    assertThat(commitAndListenersTook).isLessThan(Duration.ofSeconds(10));
  }

  private void keepEveryEmailAwayFromMandrill() {
    willReturn(Optional.empty()).given(emailService).send(any(), any(), any());
    willReturn(Optional.empty()).given(emailService).send(any(), any(), any(), any());
    willReturn(Optional.empty()).given(emailService).attemptSend(any(), any(), any());
    willReturn(false).given(emailService).sendSystemEmail(any());
    willReturn(Optional.empty()).given(emailService).cancelScheduledEmail(any());
  }

  private void changeEmailOn(Instant changedAt) {
    ClockHolder.setClock(Clock.fixed(changedAt, UTC));
    userService.changeContactDetails(
        testUser.getPersonalCode(), NEW_EMAIL, testUser.getPhoneNumber());
    ClockHolder.setClock(Clock.fixed(NOW, UTC));
  }

  private void commitTheTestTransaction() {
    testDataCommitted = true;
    TestTransaction.flagForCommit();
    TestTransaction.end();
  }

  private static void rollBackATestTransactionLeftOpen() {
    if (TestTransaction.isActive()) {
      TestTransaction.flagForRollback();
      TestTransaction.end();
    }
  }

  private void deleteWhatCommittedTestsLeft() {
    sweep(
        this::deleteLedgerTransactionsOfTheParties,
        this::deleteLedgerPartiesAndAccounts,
        this::deleteRedemptionRequests,
        this::deleteDepositPayments,
        this::deleteOnboardingStatusChanges,
        this::deleteOnboardingStatuses,
        this::deleteEmails,
        this::deleteEventLogs,
        this::deleteCompany,
        this::deleteContactChanges,
        this::deleteRequester);
  }

  private void sweep(Runnable... deletions) {
    var failures = new ArrayList<RuntimeException>();
    for (Runnable deletion : deletions) {
      try {
        deletion.run();
      } catch (RuntimeException e) {
        failures.add(e);
      }
    }
    if (failures.isEmpty()) {
      return;
    }
    var failure = failures.getFirst();
    failures.stream().skip(1).forEach(failure::addSuppressed);
    throw failure;
  }

  private void deleteLedgerTransactionsOfTheParties() {
    var ledgerTransactionIds =
        jdbcClient
            .sql(
                """
                SELECT DISTINCT related.id
                FROM ledger.transaction related
                WHERE related.external_reference IN (
                  SELECT owned.external_reference
                  FROM ledger.transaction owned
                  JOIN ledger.entry entry ON entry.transaction_id = owned.id
                  JOIN ledger.account account ON account.id = entry.account_id
                  JOIN ledger.party party ON party.id = account.owner_party_id
                  WHERE party.owner_id IN (:codes))
                """)
            .param("codes", PARTY_CODES)
            .query(UUID.class)
            .list();
    if (ledgerTransactionIds.isEmpty()) {
      return;
    }
    jdbcClient
        .sql("DELETE FROM ledger.entry WHERE transaction_id IN (:ids)")
        .param("ids", ledgerTransactionIds)
        .update();
    jdbcClient
        .sql("DELETE FROM ledger.transaction WHERE id IN (:ids)")
        .param("ids", ledgerTransactionIds)
        .update();
  }

  private void deleteLedgerPartiesAndAccounts() {
    jdbcClient
        .sql(
            "DELETE FROM ledger.account WHERE owner_party_id IN"
                + " (SELECT id FROM ledger.party WHERE owner_id IN (:codes))")
        .param("codes", PARTY_CODES)
        .update();
    jdbcClient
        .sql("DELETE FROM ledger.party WHERE owner_id IN (:codes)")
        .param("codes", PARTY_CODES)
        .update();
  }

  private void deleteRedemptionRequests() {
    jdbcClient
        .sql("DELETE FROM redemption_request WHERE party_code IN (:codes)")
        .param("codes", PARTY_CODES)
        .update();
  }

  private void deleteDepositPayments() {
    jdbcClient
        .sql("DELETE FROM saving_fund_payment WHERE party_code IN (:codes)")
        .param("codes", PARTY_CODES)
        .update();
  }

  private void deleteOnboardingStatusChanges() {
    jdbcClient
        .sql("DELETE FROM savings_fund_onboarding_status_change WHERE code IN (:codes)")
        .param("codes", PARTY_CODES)
        .update();
  }

  private void deleteOnboardingStatuses() {
    jdbcClient
        .sql("DELETE FROM savings_fund_onboarding WHERE code IN (:codes)")
        .param("codes", PARTY_CODES)
        .update();
  }

  private void deleteEmails() {
    jdbcClient
        .sql("DELETE FROM email WHERE personal_code = :personalCode")
        .param("personalCode", PERSONAL_CODE)
        .update();
  }

  private void deleteEventLogs() {
    jdbcClient
        .sql("DELETE FROM event_log WHERE principal = :personalCode")
        .param("personalCode", PERSONAL_CODE)
        .update();
  }

  private void deleteCompany() {
    jdbcClient
        .sql(
            "DELETE FROM company_party WHERE company_id IN"
                + " (SELECT id FROM company WHERE registry_code = :registryCode)")
        .param("registryCode", COMPANY_REGISTRY_CODE)
        .update();
    jdbcClient
        .sql("DELETE FROM company WHERE registry_code = :registryCode")
        .param("registryCode", COMPANY_REGISTRY_CODE)
        .update();
  }

  private void deleteContactChanges() {
    jdbcClient
        .sql(
            "DELETE FROM user_contact_change WHERE user_id IN"
                + " (SELECT id FROM users WHERE personal_code = :personalCode)")
        .param("personalCode", PERSONAL_CODE)
        .update();
  }

  private void deleteRequester() {
    jdbcClient
        .sql("DELETE FROM users WHERE personal_code = :personalCode")
        .param("personalCode", PERSONAL_CODE)
        .update();
  }

  private static ArgumentMatcher<MandrillMessage> addressedTo(String email) {
    return message -> email.equals(message.getTo().getFirst().getEmail());
  }

  private static MandrillMessageStatus acceptedFor(String email) {
    return LutungGsonUtils.getGson()
        .fromJson(
            "{\"_id\": \"accepted-"
                + email
                + "\", \"email\": \""
                + email
                + "\", \"status\": \"sent\"}",
            MandrillMessageStatus.class);
  }
}
