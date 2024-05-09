plugins {
  id("org.jetbrains.kotlin.multiplatform").version("2.0.0-RC2")
  id("com.apollographql.apollo3").version("4.0.0-beta.6")
}

kotlin {
  jvm()
  macosArm64()
  
  sourceSets {
    getByName("commonMain") {
      dependencies {
        implementation("com.apollographql.apollo3:apollo-runtime")
      }
    }
    getByName("commonTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation("com.apollographql.apollo3:apollo-runtime")
        implementation("com.apollographql.apollo3:apollo-mockserver")
        implementation("app.cash.turbine:turbine:1.1.0")
        implementation("app.cash.turbine:turbine:1.1.0")
        implementation("io.ktor:ktor-client-core:2.3.11")
        implementation("io.ktor:ktor-client-cio:2.3.11")
      }
    }
  }
}

apollo {
  service("service") {
    packageName.set("com.example")
    srcDir("graphql")
  }
}