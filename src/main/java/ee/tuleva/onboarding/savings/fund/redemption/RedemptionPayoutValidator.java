package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Everything that must hold before a redemption is priced.
 *
 * <p>These used to be discovered inside the payout loop, which runs after the units have been
 * redeemed and the cash has already moved to the withdrawal account. A request that failed there
 * left the client with neither units nor money, and nothing alerted — the ledger was consistent, so
 * the reconciler saw nothing wrong. Running them first means a payout that cannot succeed never has
 * its units redeemed in the first place.
 */
@Component
@RequiredArgsConstructor
public class RedemptionPayoutValidator {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final IbanWhitelistService ibanWhitelistService;
  private final SavingsFundLedger savingsFundLedger;

  /** Returns the reason this redemption cannot be paid, or empty when it can. */
  public Optional<String> findBlockingReason(RedemptionRequest request) {
    var party = request.getPartyId();
    var iban = request.getCustomerIban();

    if (savingsFundLedger.hasPayoutEntry(request.getId())) {
      return Optional.of("Redemption already has a payout entry in the ledger");
    }
    if (!ibanBelongsToParty(iban, request)) {
      return Optional.of("Beneficiary IBAN no longer belongs to the party");
    }
    if (savingFundPaymentRepository.findRemitterNameByIban(party, iban).isEmpty()
        && !ibanWhitelistService.isWhitelisted(party, iban)) {
      // A whitelisted IBAN may legitimately have no deposit row; the job falls back to the party's
      // registered name for those. Anything else has no name to put on the payment.
      return Optional.of("Beneficiary name is not resolvable and the IBAN is not whitelisted");
    }
    return Optional.empty();
  }

  private boolean ibanBelongsToParty(String iban, RedemptionRequest request) {
    var party = request.getPartyId();
    return savingFundPaymentRepository.findWithdrawableIbans(party).contains(iban)
        || ibanWhitelistService.isWhitelisted(party, iban);
  }
}
