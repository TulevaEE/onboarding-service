package ee.tuleva.onboarding.savings.fund.redemption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionPayoutValidatorTest {

  private static final String CUSTOMER_IBAN = "EE222222222222222222";
  private static final PartyId PARTY = new PartyId(PartyId.Type.PERSON, "38888888888");

  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private IbanWhitelistService ibanWhitelistService;
  @Mock private SavingsFundLedger savingsFundLedger;

  @InjectMocks private RedemptionPayoutValidator validator;

  @BeforeEach
  void allowByDefault() {
    lenient().when(savingsFundLedger.hasPayoutEntry(any())).thenReturn(false);
    lenient()
        .when(savingFundPaymentRepository.findWithdrawableIbans(PARTY))
        .thenReturn(List.of(CUSTOMER_IBAN));
    lenient()
        .when(savingFundPaymentRepository.findRemitterNameByIban(PARTY, CUSTOMER_IBAN))
        .thenReturn(Optional.of("Mari Maasikas"));
  }

  @Test
  void aPayableRedemptionHasNoBlockingReason() {
    assertThat(validator.findBlockingReason(request())).isEmpty();
  }

  @Test
  void blocksARedemptionTheLedgerHasAlreadyPaid() {
    var request = request();
    when(savingsFundLedger.hasPayoutEntry(request.getId())).thenReturn(true);

    assertThat(validator.findBlockingReason(request))
        .contains("Redemption already has a payout entry in the ledger");
  }

  @Test
  void blocksAnIbanThatNoLongerBelongsToTheParty() {
    when(savingFundPaymentRepository.findWithdrawableIbans(PARTY)).thenReturn(List.of());
    when(ibanWhitelistService.isWhitelisted(PARTY, CUSTOMER_IBAN)).thenReturn(false);

    assertThat(validator.findBlockingReason(request()))
        .contains("Beneficiary IBAN no longer belongs to the party");
  }

  @Test
  void allowsAWhitelistedIbanWithNoDepositOfItsOwn() {
    when(savingFundPaymentRepository.findWithdrawableIbans(PARTY)).thenReturn(List.of());
    when(savingFundPaymentRepository.findRemitterNameByIban(PARTY, CUSTOMER_IBAN))
        .thenReturn(Optional.empty());
    when(ibanWhitelistService.isWhitelisted(PARTY, CUSTOMER_IBAN)).thenReturn(true);

    assertThat(validator.findBlockingReason(request())).isEmpty();
  }

  @Test
  void amountReconcilesExactlyAgainstUnitsTimesNav() {
    var request = request();
    request.setFundUnits(new BigDecimal("100.00000"));
    request.setNavPerUnit(new BigDecimal("1.2345"));
    request.setCashAmount(new BigDecimal("123.45"));

    assertThat(request.amountReconciles()).isTrue();
  }

  @Test
  void aCentOfDriftDoesNotReconcile() {
    var request = request();
    request.setFundUnits(new BigDecimal("100.00000"));
    request.setNavPerUnit(new BigDecimal("1.2345"));
    request.setCashAmount(new BigDecimal("123.46"));

    assertThat(request.amountReconciles()).isFalse();
  }

  @Test
  void roundingIsHalfUpAtTwoPlacesJustAsPricingDoesIt() {
    var request = request();
    request.setFundUnits(new BigDecimal("3.00000"));
    request.setNavPerUnit(new BigDecimal("1.1115"));
    request.setCashAmount(new BigDecimal("3.33"));

    // 3 * 1.1115 = 3.3345 -> 3.33
    assertThat(request.amountReconciles()).isTrue();
  }

  @Test
  void anUnpricedRequestDoesNotReconcile() {
    var request = request();
    request.setCashAmount(null);
    request.setNavPerUnit(null);

    assertThat(request.amountReconciles()).isFalse();
  }

  private RedemptionRequest request() {
    var request = new RedemptionRequest();
    request.setId(UUID.randomUUID());
    request.setPartyType(PARTY.type());
    request.setPartyCode(PARTY.code());
    request.setCustomerIban(CUSTOMER_IBAN);
    request.setFundUnits(new BigDecimal("100.00000"));
    return request;
  }
}
