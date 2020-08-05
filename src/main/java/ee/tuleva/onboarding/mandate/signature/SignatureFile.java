package ee.tuleva.onboarding.mandate.signature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignatureFile {

	private final String name;
	private final String mimeType;
	private final byte[] content;

}
