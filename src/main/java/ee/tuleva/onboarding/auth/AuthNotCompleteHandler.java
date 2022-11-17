package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class AuthNotCompleteHandler implements AuthenticationFailureHandler {

  private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter =
      new OAuth2ErrorHttpMessageConverter();

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    if (exception instanceof AuthNotCompleteException) {
      writeResponse(response, HttpStatus.OK, exception);
    } else {
      writeResponse(response, HttpStatus.BAD_REQUEST, exception);
    }
  }

  private void writeResponse(
      HttpServletResponse response, HttpStatus status, AuthenticationException exception)
      throws IOException {
    OAuth2Error error = ((OAuth2AuthenticationException) exception).getError();
    ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
    httpResponse.setStatusCode(status);
    errorHttpResponseConverter.write(error, null, httpResponse);
  }
}
