package ee.tuleva.onboarding.analytics.exchange;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "exchange_transaction", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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
  private LocalDateTime dateCreated;
}
