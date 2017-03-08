package ee.tuleva.onboarding.mandate.content;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MandateContentFile {
    private String name;
    private String mimeType;
    private byte[] content;
}
