package ee.tuleva.onboarding.notification.payment;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonNaming(SnakeCaseStrategy.class)
public class Payment {

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
