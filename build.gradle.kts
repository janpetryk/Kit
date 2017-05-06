import org.gradle.jvm.tasks.Jar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

val kitVersion by project
val kotlinVersion by project
val dropwizardVersion by project

val projectTitle = "Kit"

buildscript {
    val buildscriptKotlinVersion = "1.1.1"

    repositories {
        gradleScriptKotlin()
        jcenter()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$buildscriptKotlinVersion")
        classpath("com.github.jengelman.gradle.plugins:shadow:1.2.3")
    }
}

apply {
    plugin("kotlin")
    plugin("com.github.johnrengelman.shadow")
    plugin("maven")
    plugin("maven-publish")
    plugin("jacoco")
}

jacoco {
    toolVersion = "0.7.9"
}

val jacocoTestReport = project.tasks.getByName("jacocoTestReport")

jacocoTestReport.doFirst {
    (jacocoTestReport as JacocoReport).classDirectories = fileTree("build/classes/main").apply {
        // Exclude well known data classes that should contain no logic
        // Remember to change values in codecov.yml too
        exclude("**/*Event.*")
        exclude("**/*State.*")
        exclude("**/*Configuration.*")
        exclude("**/*Runner.*")
    }

    jacocoTestReport.reports.xml.isEnabled = true
    jacocoTestReport.reports.html.isEnabled = true
}

compileJava {
    sourceCompatibility = JavaVersion.VERSION_1_7.toString()
    targetCompatibility = JavaVersion.VERSION_1_7.toString()
}

repositories {
    gradleScriptKotlin()
    mavenCentral()
}

dependencies {
    compile(kotlinModule("stdlib", kotlinVersion as String))
    compile(kotlinModule("reflect", kotlinVersion as String))

    compile("org.slf4j:slf4j-api:1.7.21")
    compile("com.google.code.gson:gson:2.7")
    compile("com.bendb.dropwizard:dropwizard-redis:$dropwizardVersion-0")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.8.7")
    compile("com.ibm.icu:icu4j:59.1")
    compile("org.bouncycastle:bcprov-jdk15on:1.56")


    testCompile("junit:junit:4.12")
    testCompile("org.mockito:mockito-core:2.2.9")
    testCompile("com.nhaarman:mockito-kotlin:1.3.0")
}

test {
    testLogging.setEvents(listOf("passed", "skipped", "failed", "standardError"))
}


val buildNumberAddition = if(project.hasProperty("BUILD_NUMBER")) { ".${project.property("BUILD_NUMBER")}" } else { "" }

version = "$kitVersion$buildNumberAddition"
group = "codes.carrot.kit"
project.setProperty("archivesBaseName", "Kit")

shadowJar {
    mergeServiceFiles()
    relocate("kotlin", "codes.carrot.kit.kotlin")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

val sourcesTask = task<Jar>("sourcesJar") {
    dependsOn("classes")

    from(sourceSets("main").allSource)
    classifier = "sources"
}

project.artifacts.add("archives", sourcesTask)
project.artifacts.add("archives", shadowJarTask())

configure<PublishingExtension> {
    val deployUrl = if (project.hasProperty("DEPLOY_URL")) { project.property("DEPLOY_URL") } else { project.buildDir.absolutePath }
    this.repositories.maven({ setUrl("$deployUrl") })

    publications {
        create<MavenPublication>("mavenJava") {
            from(components.getByName("java"))

            artifact(shadowJarTask())
            artifact(sourcesTask)

            artifactId = projectTitle
        }
    }
}

fun Project.jar(setup: Jar.() -> Unit) = (project.tasks.getByName("jar") as Jar).setup()
fun jacoco(setup: JacocoPluginExtension.() -> Unit) = the<JacocoPluginExtension>().setup()
fun shadowJar(setup: ShadowJar.() -> Unit) = shadowJarTask().setup()
fun Project.test(setup: Test.() -> Unit) = (project.tasks.getByName("test") as Test).setup()
fun Project.compileJava(setup: JavaCompile.() -> Unit) = (project.tasks.getByName("compileJava") as JavaCompile).setup()
fun shadowJarTask() = (project.tasks.findByName("shadowJar") as ShadowJar)
fun sourceSets(name: String) = (project.property("sourceSets") as SourceSetContainer).getByName(name)
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
