package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SavingFundPayment {
  UUID id;
  Long userId;

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

  @Builder.Default Status status = Status.CREATED;

  Instant statusChangedAt;
  Instant cancelledAt;

  @Nullable String returnReason;

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
