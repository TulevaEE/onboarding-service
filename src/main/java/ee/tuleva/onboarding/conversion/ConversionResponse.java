package ee.tuleva.onboarding.conversion;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class ConversionResponse {

    boolean transfersComplete;
    boolean selectionComplete;

}
