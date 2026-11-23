repositories {
    maven {
        name = "JBossReleases"
        url = uri("https://repository.jboss.org/nexus/repository/releases/")
        content {
            includeModule("org.jbpm.jbpm4", "jbpm-api")
        }
    }
}

dependencies {
    api(project(":jaybee-api"))
    implementation(project(":jaybee-core"))
    implementation(project(":jaybee-jpdl"))

    // Optional unmodified LGPL v2.1 API dependency linked dynamically by consumers at runtime.
    compileOnly("org.jbpm.jbpm4:jbpm-api:4.4")
    testImplementation("org.jbpm.jbpm4:jbpm-api:4.4")
}
