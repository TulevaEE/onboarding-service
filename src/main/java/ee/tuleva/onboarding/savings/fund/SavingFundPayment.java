package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.party.PartyId;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SavingFundPayment {
  UUID id;
  @Nullable PartyId partyId;

  BigDecimal amount;
  @Builder.Default Currency currency = EUR;

  String description;
  String remitterIban;
  String remitterName;
  @Nullable String remitterIdCode;
  String beneficiaryIban;
  String beneficiaryName;
  @Nullable String beneficiaryIdCode;

  @Nullable String externalId;
  @Nullable String endToEndId;

  Instant createdAt;
  @Nullable Instant receivedBefore;

  @Builder.Default Status status = CREATED;

  Instant statusChangedAt;
  Instant cancelledAt;

  @Nullable String returnReason;

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Nullable
  public LocalDate bookingDate() {
    return receivedBefore == null ? null : receivedBefore.atZone(ESTONIAN_ZONE).toLocalDate();
  }

  public enum Status {
    CREATED,
    RECEIVED,
    VERIFIED,
    RESERVED,
    ISSUED,
    PROCESSED,
    FROZEN,
    TO_BE_RETURNED,
    RETURNED,
  }
}
