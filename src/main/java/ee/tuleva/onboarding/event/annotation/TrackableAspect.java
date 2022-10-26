package ee.tuleva.onboarding.event.annotation;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.TrackableEventPublisher;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class TrackableAspect {

  private final TrackableEventPublisher trackableEventPublisher;

  private final ObjectMapper objectMapper;

  @Before("@annotation(trackable) && args(.., person)")
  public void track(JoinPoint joinPoint, Trackable trackable, Person person) {
    String[] argNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
    Object[] args = joinPoint.getArgs();

    Map<String, Object> params =
        IntStream.range(0, args.length - 1).boxed().collect(toMap(i -> argNames[i], i -> args[i]));

    JavaType type =
        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    Map<String, Object> data = objectMapper.convertValue(params, type);
    trackableEventPublisher.publish(person, trackable.value(), data);
  }
}
