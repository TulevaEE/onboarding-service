package ee.tuleva.onboarding.epis.mandate;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class MandateDTO {
    private final Long id;
    private final String processId;
    private final String futureContributionFundIsin;

    @NotNull
    private Instant createdDate;

    private Integer pillar = 2;

    List<MandateFundsTransferExchangeDTO> fundTransferExchanges;

    @AllArgsConstructor
    @Getter
    @Setter
    public static class MandateFundsTransferExchangeDTO {
        private String processId;
        private BigDecimal amount;
        private String sourceFundIsin;
        private String targetFundIsin;

    }
}
