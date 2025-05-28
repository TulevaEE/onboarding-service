package ee.tuleva.onboarding.swedbank.http;

import static java.util.stream.Collectors.toSet;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import java.util.Set;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
class SwedbankGatewayMarshaller {

  private final Marshaller marshaller;
  private final Unmarshaller unmarshaller;

  @SneakyThrows
  public String marshalToString(Object object) {
    StringWriter sw = new StringWriter();
    marshaller.marshal(object, sw);
    return sw.toString();
  }

  @SneakyThrows
  public <T> T unMarshal(String response, Class<T> responseClass) {
    return responseClass.cast(unmarshaller.unmarshal(new StringReader(response)));
  }

  @SneakyThrows
  public SwedbankGatewayMarshaller() {
    /*var requestClasses = findAllClassesUsingClassLoader("ee.swedbank.gateway.request");
    var responseClasses = findAllClassesUsingClassLoader("ee.swedbank.gateway.response");
    var allClasses = concat(requestClasses.stream(), responseClasses.stream()).toArray(Class[]::new);

    JAXBContext marshalContext = JAXBContext.newInstance(requestClasses);

    this.marshaller = context.createMarshaller();
    this.unmarshaller = context.createUnmarshaller();*/

    var requestClasses =
        findAllClassesUsingClassLoader("ee.swedbank.gateway.request").stream()
            .toArray(Class[]::new);
    JAXBContext marshalContext = JAXBContext.newInstance(requestClasses);
    this.marshaller = marshalContext.createMarshaller();

    var responseClasses =
        findAllClassesUsingClassLoader("ee.swedbank.gateway.response").stream()
            .toArray(Class[]::new);
    JAXBContext unMarshalContext = JAXBContext.newInstance(responseClasses);
    this.unmarshaller = unMarshalContext.createUnmarshaller();
  }

  private static Set<Class> findAllClassesUsingClassLoader(String packageName) {
    InputStream stream =
        ClassLoader.getSystemClassLoader().getResourceAsStream(packageName.replaceAll("[.]", "/"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    return reader
        .lines()
        .filter(line -> line.endsWith(".class"))
        .map(line -> getClass(line, packageName))
        .collect(toSet());
  }

  @SneakyThrows
  private static Class getClass(String className, String packageName) {
    return Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
  }
}
