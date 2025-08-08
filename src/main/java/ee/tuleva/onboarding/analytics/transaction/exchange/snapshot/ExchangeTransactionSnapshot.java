package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "exchange_transaction_snapshot", schema = "public")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeTransactionSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDateTime snapshotTakenAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDate reportingDate;

  @Column(nullable = false)
  private String securityFrom;

  @Column(nullable = false)
  private String securityTo;

  private String fundManagerFrom;
  private String fundManagerTo;

  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private BigDecimal percentage;

  @Column(nullable = false)
  private BigDecimal unitAmount;

  @Column(nullable = false)
  private LocalDateTime sourceDateCreated;
}
