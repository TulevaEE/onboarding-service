package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class MandateApplicationEvent {
    private final String name;
    private final User user;
    private final Long mandateId;
    private final byte[] signedFile;
}
