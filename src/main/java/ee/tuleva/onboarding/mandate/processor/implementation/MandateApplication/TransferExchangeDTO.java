package ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class TransferExchangeDTO implements Serializable {

    private String currency;
    private Instant date;
    private String id;
    private String documentNumber;
    private BigDecimal amount;
    private MandateApplicationStatus status;
    private String sourceFundIsin;
    private String targetFundIsin;

}
