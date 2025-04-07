package ee.tuleva.onboarding.epis.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
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
