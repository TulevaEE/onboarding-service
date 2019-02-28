package ee.tuleva.onboarding.capital;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
class InitialCapitalDto {

    private BigDecimal amount;
    private String currency;
    private BigDecimal ownershipFraction;

    static InitialCapitalDto from(InitialCapital initialCapital) {
        return builder()
            .amount(initialCapital.getAmount())
            .currency(initialCapital.getCurrency())
            .ownershipFraction(initialCapital.getOwnershipFraction())
            .build();
    }
}
