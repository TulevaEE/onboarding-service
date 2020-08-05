package ee.tuleva.onboarding.mandate.signature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

import static java.lang.Integer.parseInt;

@Getter
@RequiredArgsConstructor
public class MobileIdSignatureSession implements Serializable {

	private static final long serialVersionUID = -7443368341567864757L;

	private final int sessCode;

	private final String challenge;

	@Override
	public String toString() {
		return sessCode + ":::" + challenge;
	}

	public static MobileIdSignatureSession fromString(String serializedMobileIdSignatureSession) {
		String[] tokens = serializedMobileIdSignatureSession.split(":::");
		return new MobileIdSignatureSession(parseInt(tokens[0]), tokens[1]);
	}

}
