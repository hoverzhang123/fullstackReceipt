plugins {
    // Apply the groovy Plugin to add support for Groovy.
    groovy
    codenarc
    id("nebula.release") version "15.3.0"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    idea
    id("org.sonarqube") version "5.1.0.4882"
    jacoco
    // Ran into issue with 6.25.0 https://github.com/diffplug/spotless/issues/1658
    id("com.diffplug.spotless") version "6.9.1"
}

val artifactoryUser = System.getenv("ARTIFACTORY_USER") ?: project.property("artifactory_user") as String
val artifactoryPassword = System.getenv("ARTIFACTORY_PASSWORD") ?: project.property("artifactory_password") as String
val artifactoryServer = System.getenv("ARTIFACTORY_SERVER") ?: project.property("artifactory_server") as String

repositories {
    maven {
        url = uri("${artifactoryServer}/chewy-maven-release-virtual")
        credentials {
            username = artifactoryUser
            password = artifactoryPassword
        }
        mavenContent {
            releasesOnly()
        }
    }
    maven { url = uri("https://repo.jenkins-ci.org/releases/") }
    mavenLocal()
}

configurations { create("groovyTransformation") }

idea {
    module {
        excludeDirs = excludeDirs - file(layout.buildDirectory.get())
        val buildDir = layout.buildDirectory.get().asFile
        buildDir.listFiles { file: File, name: String -> name != "libs" }?.forEach { file: File -> excludeDirs.add(file) }
        generatedSourceDirs.add(file("${layout.buildDirectory.get()}/libs"))
    }
}

sourceSets {
    main {
        groovy {
            setSrcDirs(listOf("src", "vars"))
            exclude("test")
        }
    }

    test {
        groovy {
            setSrcDirs(listOf("src/test/groovy"))
        }
        resources {
            srcDir("src/test/resources")
        }
    }
}

spotless {
    groovy {
        greclipse()
        trimTrailingWhitespace() // Removes trailing spaces
        indentWithSpaces(4) // Ensures indentation is with 4 spaces
        endWithNewline() // Ensures file ends with a newline

        // Preserve comments by replacing spaces inside them with a placeholder
        custom("Preserve single-line comments") {
            it.replace(Regex("(//.*)"), "$1")
        }
        custom("Preserve multi-line comments") {
            it.replace(Regex("(/\\*[\\s\\S]*?\\*/)"), "$1")
        }
    }
}

codenarc {
    toolVersion = "3.5.0"
    configFile = file("${projectDir}/codenarc/rules.groovy")
}

tasks.withType<CodeNarc>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
        html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/codenarc.html"))
    }
}

dependencies {
    // Groovy dependencies
    implementation("com.cloudbees:groovy-cps:1.22")
    implementation("org.codehaus.groovy:groovy-all:2.4.21") // newer versions freeze on unit tests???

    implementation("com.google.code.gson:gson:2.11.0")
    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("com.google.guava:guava:33.2.1-jre")

    implementation("commons-lang:commons-lang:2.6")
    implementation("commons-codec:commons-codec:1.16.1")

    implementation("org.connectbot.jbcrypt:jbcrypt:1.0.0")
    implementation("org.jenkins-ci.main:jenkins-core:2.79")
    implementation("javax.servlet:javax.servlet-api:4.0.1")

    implementation("org.jenkins-ci.tools:git-parameter:0.10.0") {
        artifact { extension = "jar" }
    }
    implementation("org.jenkins-ci.plugins:git-client:5.0.0") {
        artifact { extension = "jar" }
    }
    implementation("org.jenkins-ci.plugins:git:5.5.2") {
        artifact { extension = "jar" }
    }

    // Use the awesome Spock testing and specification framework even with Java
    val junitVersion = "5.10.0"
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
    testImplementation("com.lesfurets:jenkins-pipeline-unit:1.19")

    testImplementation("commons-io:commons-io:2.15.1")

    testImplementation("org.jenkins-ci.plugins.workflow:workflow-support:839.v35e2736cfd5c") {
        artifact { extension = "jar" }
    }
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-job:1400.v7fd111b_ec82f") {
        artifact { extension = "jar" }
    }
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-api:1291.v51fd2a_625da_7") {
        artifact { extension = "jar" }
    }
}

tasks.withType<GroovyCompile>().configureEach {
    groovyOptions.configurationScript = file("groovy-transformation/src/resources/CompilerTransformation.groovy")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
    maxHeapSize = "1024m"
}

tasks.processTestResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.register("writeVersionTxt") {
    group = "Nebula Release"
    doLast {
        file("version.txt").writeText(project.version.toString())
    }
}

tasks.named("final").configure {
    finalizedBy("writeVersionTxt")
}

// Configure SonarQube
sonarqube {
    properties {
        property("sonar.projectKey", "Chewy-Inc:${rootProject.name}")
        property("sonar.projectName", rootProject.name)
        property("sonar.jacoco.xmlReportPaths", "${project.layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.sources", "src/pipelines, vars")
        property("sonar.tests", "src/test")
        property("sonar.groovy.binaries", "${project.layout.buildDirectory.get()}/classes/")
    }
}

// Configure JaCoCo
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    mustRunAfter(tasks.jar)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.sonarqube {
    dependsOn(tasks.check)
} 