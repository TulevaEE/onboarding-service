package ee.tuleva.onboarding.error.response;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@JsonInclude(NON_NULL)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ErrorResponse {
  private @Nullable String code;
  private @Nullable String message;
  private @Nullable String path;
  @Builder.Default private List<String> arguments = new ArrayList<>();
}
