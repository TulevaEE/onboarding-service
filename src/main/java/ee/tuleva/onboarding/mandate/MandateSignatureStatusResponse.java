package ee.tuleva.onboarding.mandate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MandateSignatureStatusResponse {

	private String statusCode;

}
