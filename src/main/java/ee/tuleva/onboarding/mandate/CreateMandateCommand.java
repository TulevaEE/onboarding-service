package ee.tuleva.onboarding.mandate;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

public class CreateMandateCommand {

    @NotNull
    Long targetFundId;

    @NotNull
    List<fundTransferOrder> fundTransferOrders;

    private class fundTransferOrder {
        @NotNull
        String sourceFundIsin;
        @NotNull
        @Min(1)
        @Max(100)
        Integer transferPercent;
    }

}
