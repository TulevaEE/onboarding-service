package ee.tuleva.onboarding.event.annotation;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.TrackableEvent;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Aspect
@Component
@RequiredArgsConstructor
public class TrackableAspect {

  private final ApplicationEventPublisher eventPublisher;

  private final JsonMapper objectMapper;

  @Before("@annotation(trackable) && args(.., person)")
  public void track(JoinPoint joinPoint, Trackable trackable, Person person) {
    String[] argNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
    Object[] args = joinPoint.getArgs();

    Map<String, Object> params =
        IntStream.range(0, args.length - 1).boxed().collect(toMap(i -> argNames[i], i -> args[i]));

    JavaType type =
        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    Map<String, Object> data = objectMapper.convertValue(params, type);

    eventPublisher.publishEvent(new TrackableEvent(person, trackable.value(), data));
  }
}
