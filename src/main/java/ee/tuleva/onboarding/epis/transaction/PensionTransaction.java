package ee.tuleva.onboarding.epis.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionTransaction {
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
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
  private String fundManager;
  private String fund;
  private Integer purposeCode;
  private String counterpartyName;
  private String counterpartyCode;
  private String counterpartyBankAccount;
  private String counterpartyBank;
  private String counterpartyBic;
}
