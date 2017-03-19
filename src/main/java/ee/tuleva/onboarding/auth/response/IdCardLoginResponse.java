package ee.tuleva.onboarding.auth.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IdCardLoginResponse {

    private boolean success;

    public static IdCardLoginResponse success() {
        return builder()
                .success(true)
                .build();
    }
}
