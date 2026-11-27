package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TwoConfirmationsRuleTest {

  private static final String PORTFOLIO_MANAGER = "operator-1";
  private static final String FUND_MANAGER = "operator-7";
  private static final String SUBSTITUTE = "operator-8";
  private static final String WITHOUT_OWN_TOKEN = "operator-9";
  private static final String OUTSIDE_TULEVA = "operator-10";
  private static final OrderSender FUND_MANAGERS_OWN_ADDRESS =
      new OrderSender("fundmanager@tuleva.ee", "Fund Manager");
  private static final Long VERSION = 3L;

  private static final AdminTokenAuthenticator AUTHENTICATOR =
      new AdminTokenAuthenticator(
          new AdminTokenProperties(
              "shared-token",
              "ops-token",
              Map.of(
                  PORTFOLIO_MANAGER,
                  "token-1",
                  FUND_MANAGER,
                  "token-7",
                  SUBSTITUTE,
                  "token-8",
                  OUTSIDE_TULEVA,
                  "token-10")));

  private static final Map<String, OrderSender> SEND_ACCESS =
      Map.of(
          FUND_MANAGER,
          FUND_MANAGERS_OWN_ADDRESS,
          SUBSTITUTE,
          new OrderSender("substitute@tuleva.ee", "Substitute"),
          WITHOUT_OWN_TOKEN,
          new OrderSender("notoken@tuleva.ee", "No Token"),
          "shared-admin-token",
          new OrderSender("shared@tuleva.ee", "Shared"),
          OUTSIDE_TULEVA,
          new OrderSender("someone@example.com", "Someone"));

  private final TwoConfirmationsRule rule =
      new TwoConfirmationsRule(
          AUTHENTICATOR, operator -> Optional.ofNullable(SEND_ACCESS.get(operator)));

  private final TwoConfirmationsRule ruleWhileSendAccessIsNotConnected =
      new TwoConfirmationsRule(AUTHENTICATOR, new OrderSendAccessNotConnected());

  @ParameterizedTest
  @ValueSource(strings = {PORTFOLIO_MANAGER, FUND_MANAGER})
  void letsAnyNamedOperatorGiveTheFirstConfirmation(String operator) {
    assertThatCode(() -> rule.requireAllowedToGiveFirstConfirmation(draft(), VERSION, operator))
        .doesNotThrowAnyException();
  }

  @Test
  void forbidsTheFirstConfirmationWithTheSharedAdminTokenBecauseItNamesNoPerson() {
    assertRefused(
        () -> rule.requireAllowedToGiveFirstConfirmation(draft(), VERSION, "shared-admin-token"),
        FORBIDDEN);
  }

  @Test
  void refusesAFirstConfirmationOfAVersionTheBatchHasMovedOnFrom() {
    assertRefused(
        () -> rule.requireAllowedToGiveFirstConfirmation(draft(), 2L, PORTFOLIO_MANAGER), CONFLICT);
  }

  @Test
  void recordsTheConfirmedVersionAndTheOrderTypeChangesSoFarWithTheFirstConfirmation() {
    assertThat(TwoConfirmationsRule.firstConfirmationPayload(draft(), PORTFOLIO_MANAGER, 1L))
        .isEqualTo(Map.of("actor", PORTFOLIO_MANAGER, "version", VERSION, "orderTypeChanges", 1L));
  }

  @ParameterizedTest
  @ValueSource(strings = {PORTFOLIO_MANAGER, SUBSTITUTE})
  void letsTheFundManagerSendTheVersionAnotherOperatorConfirmed(String firstConfirmer) {
    assertSends(FUND_MANAGER, firstConfirmation(firstConfirmer, VERSION, 0L), 0L);
  }

  @Test
  void sendsInTheNameOfTheAddressTheSendAccessGivesTheSender_neverAnAddressFromTheRequest() {
    assertThat(
            rule.requireAllowedToSend(
                draft(),
                VERSION,
                FUND_MANAGER,
                List.of(firstConfirmation(PORTFOLIO_MANAGER, VERSION, 0L)),
                0L))
        .isEqualTo(FUND_MANAGERS_OWN_ADDRESS);
  }

  @Test
  void forbidsSendingFromAnAddressOutsideTulevaEvenWithSendAccess() {
    assertForbiddenToSend(OUTSIDE_TULEVA, firstConfirmation(PORTFOLIO_MANAGER, VERSION, 0L), 0L);
  }

  @Test
  void letsTheFundManagerSendWhenOnlyOrderTypesChangedSinceTheFirstConfirmation() {
    assertSends(FUND_MANAGER, firstConfirmation(PORTFOLIO_MANAGER, 1L, 0L), 2L);
  }

  @Test
  void readsTheStoredNumbersWhetherTheyComeBackAsLongsOrAsIntegers() {
    assertSends(FUND_MANAGER, firstConfirmation(PORTFOLIO_MANAGER, 2, 1), 2L);
  }

  @Test
  void forbidsSendingWhenAnythingButAnOrderTypeChangedSinceTheFirstConfirmation() {
    assertForbiddenToSend(FUND_MANAGER, firstConfirmation(PORTFOLIO_MANAGER, 2L, 0L), 0L);
  }

  @Test
  void forbidsThePortfolioManagerFromSendingBecauseOnlyAnOperatorWithSendAccessSends() {
    assertForbiddenToSend(PORTFOLIO_MANAGER, firstConfirmation(FUND_MANAGER, VERSION, 0L), 0L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"shared-admin-token", WITHOUT_OWN_TOKEN})
  void forbidsSendingWithoutAnOperatorTokenOfTheirOwnWhateverTheAccessLevelsSay(String sender) {
    assertForbiddenToSend(sender, firstConfirmation(PORTFOLIO_MANAGER, VERSION, 0L), 0L);
  }

  @Test
  void refusesTheFundManagersSendAsUnavailableWhileSendAccessIsNotConnected() {
    assertRefused(
        () ->
            ruleWhileSendAccessIsNotConnected.requireAllowedToSend(
                draft(),
                VERSION,
                FUND_MANAGER,
                List.of(firstConfirmation(PORTFOLIO_MANAGER, VERSION, 0L)),
                0L),
        SERVICE_UNAVAILABLE);
  }

  @Test
  void forbidsTheSharedAdminTokenBeforeAskingTheAccessLevelsWhileSendAccessIsNotConnected() {
    assertRefused(
        () ->
            ruleWhileSendAccessIsNotConnected.requireAllowedToSend(
                draft(),
                VERSION,
                "shared-admin-token",
                List.of(firstConfirmation(PORTFOLIO_MANAGER, VERSION, 0L)),
                0L),
        FORBIDDEN);
  }

  @Test
  void forbidsSendingWithoutAFirstConfirmation() {
    assertRefused(
        () -> rule.requireAllowedToSend(draft(), VERSION, FUND_MANAGER, List.of(), 0L), FORBIDDEN);
  }

  @Test
  void forbidsSendingOnTheSendersOwnFirstConfirmation() {
    assertForbiddenToSend(FUND_MANAGER, firstConfirmation(FUND_MANAGER, VERSION, 0L), 0L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"shared-admin-token", "operator-2"})
  void forbidsSendingOnAFirstConfirmationByNoCurrentNamedOperator(String confirmer) {
    assertForbiddenToSend(FUND_MANAGER, firstConfirmation(confirmer, VERSION, 0L), 0L);
  }

  @Test
  void refusesToSendAVersionTheSenderDidNotReview() {
    assertRefused(
        () ->
            rule.requireAllowedToSend(
                draft(),
                2L,
                FUND_MANAGER,
                List.of(firstConfirmation(PORTFOLIO_MANAGER, VERSION, 0L)),
                0L),
        CONFLICT);
  }

  @Test
  void tellsAnOperatorWithoutSendAccessNoMoreThanThatEvenForAnOldVersion() {
    assertRefused(
        () ->
            rule.requireAllowedToSend(
                draft(),
                2L,
                PORTFOLIO_MANAGER,
                List.of(firstConfirmation(FUND_MANAGER, VERSION, 0L)),
                0L),
        FORBIDDEN);
  }

  private void assertSends(
      String sender, TransactionAuditEvent firstConfirmation, long orderTypeChanges) {
    assertThatCode(
            () ->
                rule.requireAllowedToSend(
                    draft(), VERSION, sender, List.of(firstConfirmation), orderTypeChanges))
        .doesNotThrowAnyException();
  }

  private void assertForbiddenToSend(
      String sender, TransactionAuditEvent firstConfirmation, long orderTypeChanges) {
    assertRefused(
        () ->
            rule.requireAllowedToSend(
                draft(), VERSION, sender, List.of(firstConfirmation), orderTypeChanges),
        FORBIDDEN);
  }

  private static void assertRefused(ThrowingCallable attempt, HttpStatus status) {
    assertThatThrownBy(attempt)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }

  private static TransactionAuditEvent firstConfirmation(
      String operator, Number version, Number orderTypeChanges) {
    return TransactionAuditEvent.builder()
        .batch(draft())
        .eventType("BATCH_FIRST_CONFIRMED")
        .actor(operator)
        .createdAt(Instant.parse("2026-11-20T10:00:00Z"))
        .payload(
            Map.of("actor", operator, "version", version, "orderTypeChanges", orderTypeChanges))
        .build();
  }

  private static TransactionBatch draft() {
    return TransactionBatch.builder()
        .id(10L)
        .fund(TUK75)
        .status(DRAFT)
        .version(VERSION)
        .createdBy(PORTFOLIO_MANAGER)
        .createdAt(Instant.parse("2026-11-20T09:00:00Z"))
        .metadata(Map.of())
        .build();
  }
}
