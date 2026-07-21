import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.jar.JarFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

group = "top.qwq2333.authsrv"
version = "0.0.1"

application {
    mainClass.set("top.qwq2333.authsrv.MainKt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        progressiveMode.set(true)
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.hikari)
    implementation(libs.mariadb)
    implementation(libs.slf4j)
    runtimeOnly(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    testRuntimeOnly(libs.h2)
    shadowR8(libs.r8)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    environment("AUTH_INITIAL_ADMIN_TOKEN", "test_root")
}

tasks.test {
    exclude("**/FatJarSmokeTest.class")
}

tasks.jar {
    enabled = false
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("authserver")
    archiveClassifier.set("all-optimized")
    @Suppress("DEPRECATION")
    enableKotlinModuleRemapping.set(false)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
    exclude(
        "META-INF/*.SF",
        "META-INF/*.RSA",
        "META-INF/*.DSA",
        "META-INF/INDEX.LIST",
        "module-info.class",
        "META-INF/versions/*/module-info.class",
        "META-INF/services/jakarta.servlet.ServletContainerInitializer",
        "META-INF/services/io.ktor.server.config.ConfigLoader",
        "META-INF/services/kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoader",
        "META-INF/services/kotlin.reflect.jvm.internal.impl.km.internal.extensions.MetadataExtensions",
        "META-INF/services/kotlin.reflect.jvm.internal.impl.resolve.ExternalOverridabilityCondition",
        "META-INF/proguard/**",
        "META-INF/com.android.tools/**",
    )
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Multi-Release" to "true",
        )
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    minimize {
        exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
        exclude(dependency("org.fusesource.jansi:jansi:.*"))
        exclude(dependency("ch.qos.logback:logback-classic:.*"))
        r8 {
            enableOptimization()
            keepRuleFiles.from(layout.projectDirectory.file("r8-rules.pro"))
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.register("verifyFatJar") {
    group = "verification"
    dependsOn("fatJarTest")
    doLast {
        val file = tasks.shadowJar.get().archiveFile.get().asFile
        JarFile(file).use { jar ->
            check(jar.manifest.mainAttributes.getValue("Main-Class") == application.mainClass.get())
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            check(
                entries.containsAll(
                    setOf(
                        "top/qwq2333/authsrv/MainKt.class",
                        "org/mariadb/jdbc/Driver.class",
                        "ch/qos/logback/classic/spi/LogbackServiceProvider.class",
                        "org/jetbrains/exposed/v1/jdbc/ExposedConnectionImpl.class",
                        "io/ktor/serialization/kotlinx/json/KotlinxSerializationJsonExtensionProvider.class",
                    ),
                ),
            )
            check(entries.any { it.startsWith("top/qwq2333/authsrv/") && it.endsWith("\$\$serializer.class") })
            check("META-INF/services/java.sql.Driver" in entries)
            check("META-INF/services/org.slf4j.spi.SLF4JServiceProvider" in entries)
            check("META-INF/services/org.jetbrains.exposed.v1.jdbc.DatabaseConnectionAutoRegistration" in entries)
            check("META-INF/services/io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider" in entries)
            jar.entries().asSequence()
                .filter { it.name.startsWith("META-INF/services/") && !it.isDirectory }
                .forEach { service ->
                    val serviceType = service.name.removePrefix("META-INF/services/")
                    if (!serviceType.startsWith("java.")) {
                        check(serviceType.replace('.', '/') + ".class" in entries)
                    }
                    jar.getInputStream(service).bufferedReader().useLines { lines ->
                        lines.map { it.substringBefore('#').trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { provider ->
                                check(provider.replace('.', '/') + ".class" in entries) {
                                    "Missing ServiceLoader provider $provider declared by ${service.name}"
                                }
                            }
                    }
                }
            check("META-INF/services/jakarta.servlet.ServletContainerInitializer" !in entries)
            check(entries.none { it.startsWith("org/h2/") })
            check(entries.none { it.startsWith("com/alibaba/fastjson/") })
            check(entries.none { it.startsWith("com/google/gson/") })
            check(entries.none { it.startsWith("com/mysql/") })
            check(entries.none { it.startsWith("io/netty/") })
            check(entries.none { it.endsWith(".SF") || it.endsWith(".RSA") || it.endsWith(".DSA") })
        }
        logger.lifecycle("Verified optimized fat JAR: ${file.absolutePath} (${file.length()} bytes)")
    }
}

val mainSourceSet = sourceSets.main.get()
val testSourceSet = sourceSets.test.get()

val fatJarTest = tasks.register<Test>("fatJarTest") {
    group = "verification"
    description = "Runs API and database smoke tests against the optimized fat JAR."
    dependsOn(tasks.shadowJar, tasks.testClasses)
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile }) + testSourceSet.output +
        (testSourceSet.runtimeClasspath - mainSourceSet.runtimeClasspath - mainSourceSet.output - testSourceSet.output)
    include("**/FatJarSmokeTest.class")
}

tasks.check {
    dependsOn("verifyFatJar")
}
