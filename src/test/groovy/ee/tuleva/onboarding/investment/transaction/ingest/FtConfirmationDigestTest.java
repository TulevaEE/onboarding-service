package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtConfirmationDigestTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 8);

  @Mock private OperationsNotificationService notificationService;

  private FtConfirmationDigest digest(boolean registryAuthoritative) {
    return new FtConfirmationDigest(notificationService, registryAuthoritative);
  }

  @Test
  void errorRow_isSentAsOneInvestmentDigest() {
    digest(false).publish(List.of(outcome(ERROR, OK)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ERROR, priceStatus=OK",
            INVESTMENT);
  }

  @Test
  void ambiguousRow_isSent() {
    digest(false).publish(List.of(outcome(AMBIGUOUS, AMBIGUOUS)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=AMBIGUOUS, priceStatus=AMBIGUOUS",
            INVESTMENT);
  }

  @Test
  void orphanRow_whileRegistryNotAuthoritative_isNotSent() {
    digest(false).publish(List.of(outcome(ORPHAN, ORPHAN)));

    verifyNoInteractions(notificationService);
  }

  @Test
  void orphanRow_whenRegistryAuthoritative_isSent() {
    digest(true).publish(List.of(outcome(ORPHAN, ORPHAN)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ORPHAN, priceStatus=ORPHAN",
            INVESTMENT);
  }

  @Test
  void cleanAndPendingRows_areNotSent() {
    digest(true).publish(List.of(outcome(OK, OK), outcome(OK, PENDING_NAV)));

    verifyNoInteractions(notificationService);
  }

  @Test
  void emptyOutcomes_areNotSent() {
    digest(true).publish(List.of());

    verifyNoInteractions(notificationService);
  }

  @Test
  void slackFailure_doesNotPropagate() {
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    assertThatCode(() -> digest(true).publish(List.of(outcome(ERROR, OK))))
        .doesNotThrowAnyException();
  }

  private static FtConfirmationOutcome outcome(
      FtVerificationStatus quantityStatus, FtVerificationStatus priceStatus) {
    FtConfirmation confirmation =
        new FtConfirmation(
            TUK75, ISIN, TRADE_DATE, new BigDecimal("40434"), new BigDecimal("10.09"));
    return new FtConfirmationOutcome(
        confirmation, new FtConfirmationResult(quantityStatus, priceStatus, Map.of()));
  }
}
