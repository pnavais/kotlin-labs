plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10" 
	id("com.github.johnrengelman.shadow") version "8.1.0"
    application 
}

repositories {
    mavenCentral() 
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5") 
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1") 
}

application {
    mainClass.set("com.github.pnavais.kotlin.minesweeper.MineSweeperKt")
}

tasks.named<JavaExec>("run") {
	standardInput = System.`in`
}

tasks.named<Test>("test") {
    useJUnitPlatform() 
}
