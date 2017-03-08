package ee.tuleva.onboarding.auth.idcard;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@RequiredArgsConstructor
@Data
@Builder
public class IdCardSession implements Serializable {

    public final String firstName;
    public final String lastName;
    public final String personalCode;

    @Override
    public String toString() {
        return firstName + ":::" + lastName + ":::" + personalCode;
    }

    public static IdCardSession fromString(String serializedSession) {
        String[] tokens = serializedSession.split(":::");
        return builder()
                .firstName(tokens[0])
                .lastName(tokens[1])
                .personalCode(tokens[2])
                .build();
    }
}