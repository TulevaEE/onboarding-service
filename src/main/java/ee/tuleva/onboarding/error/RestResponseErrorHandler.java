package ee.tuleva.onboarding.error;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestResponseErrorHandler extends DefaultResponseErrorHandler {

  private final ObjectMapper mapper;

  @Override
  public void handleError(URI url, HttpMethod method, ClientHttpResponse response)
      throws IOException {
    HttpStatusCode statusCode = response.getStatusCode();
    if (statusCode.is4xxClientError() || statusCode == INTERNAL_SERVER_ERROR) {
      String responseBody = StreamUtils.copyToString(response.getBody(), UTF_8);

      ErrorsResponse errorsResponse;
      try {
        errorsResponse = mapper.readValue(responseBody, ErrorsResponse.class);
      } catch (Exception jsonParsingException) {
        log.error(
            "Failed to parse error response as JSON for status {}: {}. Response body: {}",
            statusCode,
            jsonParsingException.getMessage(),
            responseBody,
            jsonParsingException);

        throw new IOException(
            "Failed to parse error response JSON: " + jsonParsingException.getMessage(),
            jsonParsingException);
      }
      throw new ErrorsResponseException(errorsResponse);
    }

    super.handleError(url, method, response);
  }
}
