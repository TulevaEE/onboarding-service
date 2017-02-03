package ee.tuleva.onboarding.mandate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import static java.lang.Integer.parseInt;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MandateSignatureSession implements Serializable {

	public int sessCode;

	public String challenge;

	@Override
	public String toString() {
		return sessCode + ":::" + challenge;
	}

	public static MandateSignatureSession fromString(String serializedMobileIDSession) {
		String[] tokens = serializedMobileIDSession.split(":::");
		return builder()
				.sessCode(parseInt(tokens[0]))
				.challenge(tokens[1])
				.build();
	}

}
