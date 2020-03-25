import org.springframework.boot.gradle.tasks.buildinfo.BuildInfo

plugins {
  java
  id("org.springframework.boot") version "2.3.0.M3"
  id("io.spring.dependency-management") version "1.0.9.RELEASE"
}

group = "eu.maksimov.demo"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-starter-web")

  implementation("org.springdoc:springdoc-openapi-ui:1.2.34")

  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.assertj:assertj-core")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Test> {
  useJUnitPlatform()
}

springBoot {
  buildInfo()
}

tasks.withType<BuildInfo> {
  // so that dummy src/main/resources/META-INF/build-info.properties gets overridden
  mustRunAfter(tasks.processResources)
}

tasks.wrapper {
  distributionType = Wrapper.DistributionType.ALL
  version = "6.3"
}
