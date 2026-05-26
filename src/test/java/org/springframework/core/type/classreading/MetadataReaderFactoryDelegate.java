package org.springframework.core.type.classreading;

import org.springframework.core.io.ResourceLoader;

// Test-only shadow of Spring's package-private MetadataReaderFactoryDelegate. It forces the
// ASM-based SimpleMetadataReaderFactory instead of the JDK ClassFile API reader that Spring
// Framework 7 selects on JDK 24+. The ClassFile reader pins parsed class readers (raw bytes +
// constant pool) via bound annotations retained in bean definitions / per-context resource
// caches, which blows up heap when many Spring test contexts are cached and OOMs CI.
// See https://github.com/spring-projects/spring-framework/issues/36737.
//
// Lives on the test classpath only (loaded before spring-core.jar), so the production artifact
// keeps Spring's default reader. Remove once spring-framework#36737 is resolved.
abstract class MetadataReaderFactoryDelegate {

  static MetadataReaderFactory create(ResourceLoader resourceLoader) {
    return new SimpleMetadataReaderFactory(resourceLoader);
  }

  static MetadataReaderFactory create(ClassLoader classLoader) {
    return new SimpleMetadataReaderFactory(classLoader);
  }
}
