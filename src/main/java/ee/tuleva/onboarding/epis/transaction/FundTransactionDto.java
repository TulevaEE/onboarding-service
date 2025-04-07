package ee.tuleva.onboarding.epis.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FundTransactionDto {
  private LocalDate date;
  private String personName;
  private String personId;
  private String pensionAccount;
  private String country;
  private String transactionType;
  private String purpose;
  private String applicationType;
  private BigDecimal unitAmount;
  private BigDecimal price;
  private BigDecimal nav;
  private BigDecimal amount;
  private BigDecimal serviceFee;
}
