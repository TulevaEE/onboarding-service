package ee.tuleva.onboarding.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestResponseErrorHandler extends DefaultResponseErrorHandler {

  private final ObjectMapper mapper;

  @Override
  public void handleError(ClientHttpResponse response) throws IOException {
    ErrorsResponse errorsResponse = mapper.readValue(response.getBody(), ErrorsResponse.class);
    throw new ErrorsResponseException(errorsResponse);
  }
}
