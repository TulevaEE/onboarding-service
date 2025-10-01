package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SavingFundPayment {
  UUID id;
  BigDecimal amount;

  @Builder.Default
  Currency currency = EUR;

  String description;
  String remitterIban;
  String remitterName;
  @Nullable String remitterIdCode;
  String beneficiaryIban;
  String beneficiaryName;
  @Nullable String beneficiaryIdCode;

  @Nullable String externalId;

  Instant createdAt;

  @Builder.Default
  Status status = Status.CREATED;

  Instant statusChangedAt;

  public enum Status {

    CREATED,
    RECEIVED,
    VERIFIED,
    RESERVED,
    PROCESSED,
    FROZEN,
    TO_BE_RETURNED,
    RETURNED,

  }

}
