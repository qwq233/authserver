/*
 * qwq233
 * Copyright (C) 2019-2021 qwq233@qwq2333.top
 * https://qwq2333.top
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by qwq233.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/qwq233/qwq233/blob/master/eula.md>.
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
    }
}
plugins {
    java
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
    application
    id("org.gradle.kotlin.kotlin-dsl")
}

kotlinDslPluginOptions {
	jvmTarget.set(provider { java.targetCompatibility.toString() })
}

val mainClazzName: String = "top.qwq2333.authsrv.MainKt"
group "nil.nadph.authsrv"
version "0.0.1"

project.setProperty("mainClassName", mainClazzName)
application {
    // Define the main class for the application
    mainClassName = mainClazzName
    mainClass.set(mainClassName)
}
tasks.withType<ShadowJar>() {
    manifest {
        attributes["Main-Class"] = mainClazzName
    }
}
tasks.withType<Jar>() {
    manifest {
        attributes["Main-Class"] = mainClazzName
    }
}
sourceSets {
    main {
        java { srcDir("src/java")}
        resources { srcDir("src/resource")}
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(Kotlin.stdlib)
    implementation(Ktor.server.netty)
    implementation(Ktor.server.core)
    implementation(Ktor.features.gson)
    implementation(Ktor.client.core)
    implementation("io.ktor:ktor-client-core-jvm:_")
    implementation(Ktor.client.apache)
    implementation(Ktor.client.serialization)
    implementation("io.ktor:ktor-client-gson:_")
    implementation("mysql:mysql-connector-java:_")
    implementation("com.zaxxer:HikariCP:_")
    implementation("commons-io:commons-io:_")
    // https://mvnrepository.com/artifact/com.alibaba/fastjson
    implementation("com.alibaba:fastjson:_")
    // https://mvnrepository.com/artifact/org.jetbrains/annotations
    implementation("org.jetbrains:annotations:_")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-log4j12
    implementation("org.slf4j:slf4j-log4j12:_")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    implementation("org.slf4j:slf4j-api:_")
}
dependencies {
    implementation("mysql:mysql-connector-java:_")
    implementation("com.zaxxer:HikariCP:_")
    implementation("com.alibaba:fastjson:_")
    implementation("commons-io:commons-io:_")
    // https://mvnrepository.com/artifact/org.jetbrains/annotations
    implementation("org.jetbrains:annotations:_")
    implementation("org.apache.logging.log4j:log4j-api:_")
    implementation("org.apache.logging.log4j:log4j-core:_")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    implementation("org.slf4j:slf4j-api:_")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-log4j12
    implementation("org.slf4j:slf4j-log4j12:_")

}
