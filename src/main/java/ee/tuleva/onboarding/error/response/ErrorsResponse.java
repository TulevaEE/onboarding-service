package ee.tuleva.onboarding.error.response;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ErrorsResponse {

  private List<ErrorResponse> errors = new ArrayList<>();

  public static ErrorsResponse ofSingleError(String code, String message) {
    return new ErrorsResponse(List.of(ErrorResponse.builder().code(code).message(message).build()));
  }

  public static ErrorsResponse ofSingleError(ErrorResponse error) {
    return new ErrorsResponse(List.of(error));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public void add(ErrorResponse error) {
    errors.add(error);
  }
}
