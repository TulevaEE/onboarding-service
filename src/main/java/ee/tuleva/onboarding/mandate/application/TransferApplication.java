package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@ToString(callSuper = true)
public class TransferApplication extends Application {

  private final TransferApplicationDetails details;
  @Builder.Default private final ApplicationType type = TRANSFER;

  public List<Exchange> getExchanges() {
    return details.getExchanges();
  }
}
