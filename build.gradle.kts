plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val gsonVersion = "2.11.0"
val httpClient5Version = "5.3.1"
val slf4jSimpleVersion = "2.0.13"

dependencies {
    implementation("org.slf4j:slf4j-simple:$slf4jSimpleVersion")
    implementation("org.apache.httpcomponents.client5:httpclient5:$httpClient5Version")
    implementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}