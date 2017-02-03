package ee.tuleva.onboarding.mandate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MandateSignatureSession {

	public int sessCode;

	public String challenge;

}
