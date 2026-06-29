package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;

import ee.tuleva.onboarding.party.PartyId;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "saving_fund_iban_whitelist")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
class SavingFundIbanWhitelist {

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @NotNull
  @Enumerated(STRING)
  @Column(nullable = false)
  private PartyId.Type partyType;

  @NotNull
  @Column(nullable = false)
  private String partyCode;

  @NotNull
  @Column(nullable = false)
  private String iban;

  @Column private String comment;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
