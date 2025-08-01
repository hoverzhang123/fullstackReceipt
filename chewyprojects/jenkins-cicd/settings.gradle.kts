pluginManagement {
    val jfrogUser = System.getenv("ARTIFACTORY_USER") ?: extra["artifactory_user"] as String
    val jfrogPassword = System.getenv("ARTIFACTORY_PASSWORD") ?: extra["artifactory_password"] as String
    val jfrogServer = System.getenv("ARTIFACTORY_SERVER") ?: extra["artifactory_server"] as String

    repositories {
        maven {
            url = uri("${jfrogServer}/plugins-release")
            credentials {
                username = jfrogUser
                password = jfrogPassword
            }
        }
    }
}

rootProject.name = "jenkins-cicd" 