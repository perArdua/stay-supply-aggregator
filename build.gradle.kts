plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.perardua"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webclient")
	implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
	implementation("io.github.resilience4j:resilience4j-reactor:2.3.0")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-flyway")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
	compileOnly("org.projectlombok:lombok")
	testAndDevelopmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

// Docker가 없는 환경에서도 ./gradlew build가 통과해야 하므로
// 기본 test는 Docker 없이 도는 것만 돌린다.
tasks.test {
	useJUnitPlatform {
		excludeTags("docker")
	}
}

// DataSource가 필요한 통합 테스트. 프로필별 빈 조립, 트랜잭션 프록시, 끝단 흐름을 본다.
// compose가 MySQL을 띄우므로 Docker 데몬이 있어야 한다.
tasks.register<Test>("integrationTest") {
	group = "verification"
	description = "Docker가 필요한 통합 테스트만 실행한다"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("docker")
	}
	shouldRunAfter(tasks.test)
}
