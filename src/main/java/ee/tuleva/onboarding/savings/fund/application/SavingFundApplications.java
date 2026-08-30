package ee.tuleva.onboarding.savings.fund.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.company.BoardMembershipService;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.mandate.application.Application;
import ee.tuleva.onboarding.mandate.application.SavingsApplications;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.PendingRedemption;
import ee.tuleva.onboarding.savings.RedemptionQueries;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class SavingFundApplications implements SavingsApplications {

  private final SavingFundDeadlinesService savingFundDeadlinesService;
  private final SavingFundPaymentQueries savingFundPaymentQueries;
  private final RedemptionQueries savingFundRedemptionQueries;
  private final BoardMembershipService boardMembershipService;

  @Override
  public List<? extends Application<?>> getApplications(AuthenticatedPerson person) {
    var activeParty = person.toPartyId();
    if (activeParty.type() == PartyId.Type.LEGAL_ENTITY
        && !boardMembershipService.isBoardMember(person.getPersonalCode(), activeParty.code())) {
      log.info(
          "Skipping savings-fund applications for stale legal-entity role: personalCode={}, registryCode={}",
          person.getPersonalCode(),
          activeParty.code());
      return List.of();
    }
    var payments = savingFundPaymentQueries.getPendingPayments(activeParty);
    var redemptionRequests = savingFundRedemptionQueries.getPendingRedemptions(activeParty);
    var applications = new ArrayList<Application<?>>();
    for (var payment : payments) {
      applications.add(convertSavingFundPayment(payment));
    }
    for (var redemption : redemptionRequests) {
      applications.add(convertSavingFundRedemptionRequest(redemption));
    }
    return applications;
  }

  private Application<SavingFundPaymentApplicationDetails> convertSavingFundPayment(
      SavingFundPayment payment) {
    final var applicationBuilder =
        Application.<SavingFundPaymentApplicationDetails>builder()
            .creationTime(payment.getCreatedAt())
            .status(PENDING)
            // Only used for front-end uniqueness, otherwise meaningless
            .id(payment.getId().getMostSignificantBits());

    var cancellationDeadline =
        savingFundDeadlinesService.getCancellationDeadline(payment).minusSeconds(1);

    applicationBuilder.details(
        SavingFundPaymentApplicationDetails.builder()
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentId(payment.getId())
            .cancelledAt(payment.getCancelledAt())
            .cancellationDeadline(payment.getCancelledAt() != null ? null : cancellationDeadline)
            .fulfillmentDeadline(savingFundDeadlinesService.getFulfillmentDeadline(payment))
            .build());
    return applicationBuilder.build();
  }

  private Application<SavingFundWithdrawalApplicationDetails> convertSavingFundRedemptionRequest(
      PendingRedemption redemption) {
    return Application.<SavingFundWithdrawalApplicationDetails>builder()
        .creationTime(redemption.requestedAt())
        .status(PENDING)
        .id(redemption.id().getMostSignificantBits())
        .details(
            SavingFundWithdrawalApplicationDetails.builder()
                .id(redemption.id())
                .amount(redemption.amount())
                .currency(Currency.EUR)
                .iban(redemption.customerIban())
                .cancellationDeadline(redemption.cancellationDeadline().minusSeconds(1))
                .fulfillmentDeadline(redemption.fulfillmentDeadline())
                .build())
        .build();
  }
}
