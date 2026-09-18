package ee.tuleva.onboarding.savings.fund.gift;

import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "savings_fund_gift")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gift {

  public static final int MAX_MESSAGE_LENGTH = 300;

  @Id
  @GeneratedValue(strategy = UUID)
  private java.util.UUID id;

  @NotNull
  @Column(nullable = false)
  private java.util.UUID giftLinkId;

  @NotNull
  @Column(nullable = false)
  private String description;

  @NotNull
  @Column(nullable = false)
  private BigDecimal amount;

  private @Nullable String message;

  @Column(nullable = false, updatable = false)
  private @Nullable Instant createdAt;
}
