package ee.tuleva.onboarding.notification.payment;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import javax.validation.constraints.Min;
import lombok.Data;
import lombok.ToString;

@Data
@JsonNaming(SnakeCaseStrategy.class)
@ToString
public class MemberPayment {

  private static final int MIN_AMOUNT = 125;

  @Min(MIN_AMOUNT)
  private BigDecimal amount;

  private String currency;
  private String customerName;
  private String merchantData;
  private String messageTime;
  private String messageType;
  private Long reference;
  private String shop;
  private String signature;
  private String status;
  private String transaction;
}
