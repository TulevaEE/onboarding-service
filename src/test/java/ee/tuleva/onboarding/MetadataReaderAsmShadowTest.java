package ee.tuleva.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;

// Guards the test-only MetadataReaderFactoryDelegate shadow that forces ASM-based metadata
// reading (see spring-framework#36737). If this fails, the shadow stopped taking effect — likely
// a Spring upgrade changed MetadataReaderFactoryDelegate; re-check whether #36737 is fixed before
// deleting the shadow.
class MetadataReaderAsmShadowTest {

  @Test
  void usesAsmSimpleMetadataReaderNotJdkClassFileReader() throws Exception {
    var factory = new CachingMetadataReaderFactory(new DefaultResourceLoader());
    MetadataReader reader = factory.getMetadataReader(getClass().getName());
    assertThat(reader.getClass().getSimpleName()).isEqualTo("SimpleMetadataReader");
  }
}
