package ee.tuleva.onboarding.analytics.thirdpillar;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "third_pillar_transactions", schema = "analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsThirdPillarTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDate reportingDate;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String personalId;

  @Column(nullable = false)
  private String accountNo;

  private String country;

  @Column(nullable = false)
  private String transactionType;

  private String transactionSource;

  private String applicationType;

  @Column(nullable = false)
  private BigDecimal shareAmount;

  @Column(nullable = false)
  private BigDecimal sharePrice;

  @Column(nullable = false)
  private BigDecimal nav;

  @Column(nullable = false)
  private BigDecimal transactionValue;

  private BigDecimal serviceFee;

  @Column(nullable = false)
  private LocalDateTime dateCreated;

  private String fundManager;

  private String fund;

  private Integer purposeCode;

  private String counterpartyName;

  private String counterpartyCode;

  private String counterpartyBankAccount;

  private String counterpartyBank;

  private String counterpartyBic;
}
