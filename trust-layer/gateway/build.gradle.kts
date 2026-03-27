plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
    }
}

dependencies {
    implementation(project(":common")) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-security")
    }
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
}
