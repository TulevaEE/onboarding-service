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

/**
 * A capability that lets whoever holds the token pay into one child's savings fund account without
 * logging in.
 *
 * <p>The link says who the money is for and nothing else. It does not decide whether the deposit is
 * allowed: that is settled when the payment arrives, by the same rule that governs every other
 * third-party deposit. A link for a child who has since turned 18 therefore still opens, and the
 * gift is returned instead.
 */
@Entity
@Table(name = "savings_fund_gift_link")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// The token is the whole secret, so it has no business appearing in a log line.
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

  /**
   * The recipient's code while this link is open, null once it is closed.
   *
   * <p>Bookkeeping in service of a database guarantee: a unique constraint over this column lets a
   * child have any number of closed links but only one open one. Postgres would express that as a
   * partial index, which the tests' H2 does not have.
   */
  private @Nullable String openForRecipient;

  public boolean isOpen() {
    return closedAt == null;
  }

  void close(Instant at) {
    this.closedAt = at;
    this.openForRecipient = null;
  }
}
