package ee.tuleva.onboarding.auth.authority;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import java.text.ParseException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class Authority {

  public static final String USER = "USER";
  public static final String WARD = "WARD";
  public static final String MEMBER = "MEMBER";
  public static final String PARTNER = "PARTNER";
  public static final String SERVICE = "SERVICE";
  public static final String CONNECTED_APP = "CONNECTED_APP";
  public static final String CONNECTED_APP_ACCESS_TOKEN_TYPE = "at+jwt";

  public static boolean isConnectedApp(@Nullable Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(CONNECTED_APP::equals);
  }

  public static boolean isConnectedAppAccessToken(String token) {
    try {
      var header = JWSObject.parse(token).getHeader();
      return JWSAlgorithm.ES384.equals(header.getAlgorithm())
          && new JOSEObjectType(CONNECTED_APP_ACCESS_TOKEN_TYPE).equals(header.getType());
    } catch (ParseException notAJws) {
      return false;
    }
  }
}
