package ee.tuleva.onboarding.aml.alert;

import java.math.BigDecimal;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AmlThresholdAlertEvent extends ApplicationEvent {

  private final AmlAlertType type;
  private final String personalId;
  private final BigDecimal amount;
  private final String reference;
  private final AlertPartyType partyType;

  public AmlThresholdAlertEvent(
      Object source,
      AmlAlertType type,
      String personalId,
      BigDecimal amount,
      String reference,
      AlertPartyType partyType) {
    super(source);
    this.type = type;
    this.personalId = personalId;
    this.amount = amount;
    this.reference = reference;
    this.partyType = partyType;
  }
}
