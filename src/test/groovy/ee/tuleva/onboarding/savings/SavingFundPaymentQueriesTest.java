package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.CREATED;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment.Status;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SavingFundPaymentQueriesTest {

  private static final Instant NOW = Instant.parse("2026-09-16T09:00:00Z");

  SavingFundPaymentRepository repository = mock(SavingFundPaymentRepository.class);
  Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  SavingFundPaymentQueries queries = new SavingFundPaymentQueries(repository, clock);

  PartyId partyId = new PartyId(PERSON, "38888888888");

  @Test
  void getPendingPayments_hidesPaymentsTheBankNeverConfirmedWithinAWeek() {
    var awaitingConfirmation = aPayment().status(CREATED).createdAt(NOW.minus(days(6))).build();
    var atTheWindowEdge = aPayment().status(CREATED).createdAt(NOW.minus(days(7))).build();
    var abandoned = aPayment().status(CREATED).createdAt(NOW.minus(days(8))).build();
    given(repository.findPaymentsWithStatus(eq(partyId), any(Status[].class)))
        .willReturn(List.of(awaitingConfirmation, atTheWindowEdge, abandoned));

    var payments = queries.getPendingPayments(partyId);

    assertThat(payments).containsExactly(awaitingConfirmation, atTheWindowEdge);
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"RECEIVED", "VERIFIED", "RESERVED", "FROZEN", "TO_BE_RETURNED"})
  void getPendingPayments_keepsOldPaymentsTheBankAlreadyConfirmed(Status status) {
    var confirmedLongAgo = aPayment().status(status).createdAt(NOW.minus(days(30))).build();
    given(repository.findPaymentsWithStatus(eq(partyId), any(Status[].class)))
        .willReturn(List.of(confirmedLongAgo));

    var payments = queries.getPendingPayments(partyId);

    assertThat(payments).containsExactly(confirmedLongAgo);
  }

  private Duration days(int days) {
    return Duration.ofDays(days);
  }
}
