package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "saving_fund_payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingFundPayment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @Column(nullable = false)
  private BigDecimal amount;

  @NotNull
  @Enumerated(STRING)
  @Builder.Default
  private Currency currency = EUR;

  @Nullable @Column private String description;

  @Nullable @Column private String remitterIban;

  @Nullable @Column private String remitterIdCode;

  @Nullable @Column private String remitterName;

  @Nullable @Column private String beneficiaryIban;

  @Nullable @Column private String beneficiaryIdCode;

  @Nullable @Column private String beneficiaryName;

  @Nullable
  @Column(name = "external_id")
  private String externalId;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

  @Nullable
  @Column(name = "received_at", columnDefinition = "TIMESTAMPTZ")
  private Instant receivedAt;
}
