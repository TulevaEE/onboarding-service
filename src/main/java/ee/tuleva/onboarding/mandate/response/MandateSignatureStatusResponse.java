package ee.tuleva.onboarding.mandate.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MandateSignatureStatusResponse {

	private String statusCode;

}
