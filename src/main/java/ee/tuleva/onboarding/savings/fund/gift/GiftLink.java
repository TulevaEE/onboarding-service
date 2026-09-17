package ee.tuleva.onboarding.savings.fund.gift;

import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "savings_fund_gift_link")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "token")
public class GiftLink {

  @Id
  @GeneratedValue(strategy = UUID)
  private java.util.UUID id;

  @NotNull
  @Column(nullable = false)
  private String token;

  @NotNull
  @Column(nullable = false)
  private String recipientPersonalCode;

  @NotNull
  @Column(nullable = false)
  private String createdByPersonalCode;

  @Column(nullable = false, updatable = false)
  private @Nullable Instant createdAt;

  private @Nullable Instant closedAt;

  // Carries the recipient's code only while the link is open, so a plain unique constraint gives
  // one open link per child. Postgres would say that with a partial index, which the tests' H2
  // does not have.
  private @Nullable String openForRecipient;

  public boolean isOpen() {
    return closedAt == null;
  }

  void close(Instant at) {
    this.closedAt = at;
    this.openForRecipient = null;
  }
}
