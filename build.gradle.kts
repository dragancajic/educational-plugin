buildscript {
    ext.pycharmSandbox = project.buildDir.absolutePath + File.separator + "pycharm-sandbox"
    ext.studioSandbox = project.buildDir.absolutePath + File.separator + "studio-sandbox"
    ext.webStormSandbox = project.buildDir.absolutePath + File.separator + "webstorm-sandbox"
    ext.clionSandbox = project.buildDir.absolutePath + File.separator + "clion-sandbox"
    ext.kotlinVersion = "1.3.11"
    ext.jacksonVersion = "2.9.5"
    repositories {
        maven {
            url "https://plugins.gradle.org/m2/"
        }
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
    }
}

plugins {
    id "org.jetbrains.intellij" version "0.4.5" apply false
    id "java"
    id "idea"
    id "de.undercouch.download" version "3.4.3"
    id 'net.saliman.properties' version '1.4.6'
}


group = 'org.jetbrains.edu'
def buildNumber = System.getenv("BUILD_NUMBER")
version = "$pluginVersion-${buildNumber == null ? "SNAPSHOT" : buildNumber}"

import de.undercouch.gradle.tasks.download.Download
import org.apache.tools.ant.taskdefs.condition.Os
import groovy.io.FileType

String downloadStudioIfNeededAndGetPath() {
    if (!rootProject.hasProperty("studioVersion")) throw new IllegalStateException("studioVersion is unspecified")
    if (!rootProject.hasProperty("studioBuildVersion")) throw new IllegalStateException("studioBuildVersion is unspecified")

    def studioFolder = null
    
    for (entry in ["zip": this.&zipTree, "tar.gz": this.&tarTree]) {
        def archiveType = entry.key
        def fileTreeMethod = entry.value
        def studioArchive = file("${rootProject.projectDir}/dependencies/studio-$studioVersion-$studioBuildVersion-${osFamily}.$archiveType")
        if (!studioArchive.exists()) {
            try {
                download {
                    src studioArtifactDownloadPath(archiveType)
                    dest studioArchive
                }
            }
            catch (IllegalStateException e) {
                continue
            }
        }

        studioFolder = file("${rootProject.projectDir}/dependencies/studio-$studioVersion-$studioBuildVersion")
        if (!studioFolder.exists()) {
            copy {
                from fileTreeMethod(studioArchive)
                into studioFolder
            }
        }
    }
    
    if (studioFolder == null) throw new IllegalStateException("Failed to download studio-$studioVersion-$studioBuildVersion-${osFamily}")
    return studioPath(studioFolder)
}

@SuppressWarnings("GrMethodMayBeStatic") // uses {studioBuildVersion}
private String studioArtifactDownloadPath(String archiveType) {
    def osFamily = getOsFamily()
    if (osFamily == null) throw new IllegalStateException("current os family is unsupported")

    if (inJetBrainsNetwork()) {
        return "https://repo.labs.intellij.net/edu-tools/android-studio-ide-${studioBuildVersion}-${osFamily}.$archiveType"
    }
    else {
        return "http://dl.google.com/dl/android/studio/ide-zips/${studioVersion}/android-studio-ide-${studioBuildVersion}-${osFamily}.$archiveType"
    }
}

String studioPath() {
    if (project.hasProperty("androidStudioPath")) {
        return androidStudioPath
    } else {
        return downloadStudioIfNeededAndGetPath()
    }    
}

static boolean inJetBrainsNetwork() {
    def inJetBrainsNetwork = false
    try {
        inJetBrainsNetwork = InetAddress.getByName("repo.labs.intellij.net").isReachable(1000)
        if (!inJetBrainsNetwork && org.gradle.internal.os.OperatingSystem.current().isWindows()) {
            inJetBrainsNetwork = Runtime.getRuntime().exec("ping -n 1 repo.labs.intellij.net").waitFor() == 0
        }
    }
    catch (UnknownHostException ignored) {
    }
    return inJetBrainsNetwork
}

static String getOsFamily() {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) return "windows"
    if (Os.isFamily(Os.FAMILY_MAC)) return "mac"
    if (Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC)) return "linux"
    return null
}

static String studioPath(File studioFolder) {
    def osFamily = getOsFamily()
    if (osFamily == null) throw new IllegalStateException("current os family is unsupported")
    if (osFamily == "mac") {
        def candidates = []
        studioFolder.eachFileMatch(FileType.DIRECTORIES, ~/Android Studio.*\.app/) {
            candidates << it
        }
        switch (candidates.size()) {
            case 0: throw new IllegalStateException("Can't find any folder matching `Android Studio*.app` in `$studioFolder`")
            case 1: return "${candidates[0]}/Contents"
            default: throw new IllegalStateException("More than one folder matching `Android Studio*.app` found in `$studioFolder`")
        }
    } else {
        return "$studioFolder/android-studio"
    }
}

task downloadJavaFx() {
    doLast {
        download {
            overwrite true
            src "http://download.jetbrains.com/idea/open-jfx/javafx-sdk-overlay.zip"
            dest "${project.buildDir}/javafx.zip"
        }
    }
}

task prepareJavaFx(type: Copy) {
    def javafxFile = file("${project.buildDir}/javafx.zip")
    onlyIf { javafxFile.exists() }
    from zipTree(javafxFile)
    into file("${project.buildDir}/javafx")
}

prepareJavaFx.dependsOn downloadJavaFx

def baseVersion = baseVersion()
def isJvmBasedIDE = baseIDE in ["idea", "studio"]

String baseVersion() {
    def version
    switch (baseIDE) {
        case "idea":
            version = ideaVersion
            break
        case "clion":
            version = clionVersion
            break
        case "studio":
            // Just for consistency. Value is not used
            version = studioVersion
            break
        default:
            throw InvalidUserDataException("Unexpected IDE name: $baseIDE")
    }
    return version
}

allprojects {
    apply plugin: "org.jetbrains.intellij"
    apply plugin: "java"
    apply plugin: "kotlin"
    apply plugin: 'net.saliman.properties'

    tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }
    targetCompatibility = '1.8'
    sourceCompatibility = '1.8'

    repositories {
        mavenCentral()
        maven { url 'https://dl.bintray.com/jetbrains/markdown' }
        maven { url 'https://dl.bintray.com/kotlin/kotlin-js-wrappers/' }
    }

    intellij {
        if (baseIDE == "studio") {
            localPath studioPath()
        } else {
            version = baseVersion
        }
        
        buildSearchableOptions.enabled = findProperty("enableBuildSearchableOptions") != "false"
    }

    compileKotlin {
        // see https://youtrack.jetbrains.com/issue/KT-19737
        destinationDir = compileJava.destinationDir
        kotlinOptions {
            jvmTarget = "1.8"
            languageVersion = "1.2"
            apiVersion = "1.2"
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            languageVersion = "1.2"
            apiVersion = "1.2"
        }
    }

    dependencies {
        compileOnly "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"
        compile group: 'org.twitter4j', name: 'twitter4j-core', version: '4.0.1'
        compile 'org.jsoup:jsoup:1.11.2'
        compile("org.jetbrains:markdown:0.1.28") {
            exclude module: 'kotlin-runtime'
            exclude module: 'kotlin-reflect'
            exclude module: 'kotlin-stdlib'
            exclude module: 'kotlin-stdlib-common'
            exclude module: 'kotlin-stdlib-jdk8'
        }
        
        compile(group: 'com.fasterxml.jackson.dataformat', name: 'jackson-dataformat-yaml', version: jacksonVersion) {
            exclude module: 'snakeyaml'
        }
        //transitive dependency is specified explicitly to avoid conflict with lib bundled since idea 181
        compile group: 'com.fasterxml.jackson.core', name: 'jackson-core', version: jacksonVersion

        //transitive dependency is specified explicitly because of the issue https://github.com/FasterXML/jackson-dataformats-text/issues/81
        //intellij platform uses affected snakeyaml version inside
        compile group: 'org.yaml', name: 'snakeyaml', version: '1.19'
        compile(group: 'com.fasterxml.jackson.module', name: 'jackson-module-kotlin', version: jacksonVersion) {
            exclude module: 'kotlin-runtime'
            exclude module: 'kotlin-reflect'
            exclude module: 'kotlin-stdlib'
            exclude module: 'kotlin-stdlib-common'
            exclude module: 'kotlin-stdlib-jdk8'
        }

        compile 'com.squareup.retrofit2:retrofit:2.4.0'
        compile 'com.squareup.retrofit2:converter-jackson:2.3.0'
        compile 'com.squareup.retrofit2:converter-gson:2.4.0'
        compile 'com.squareup.okhttp3:logging-interceptor:3.13.0'

        compileOnly fileTree(dir: "${rootProject.buildDir}/javafx/jre/lib/ext")
        
        compile ('org.jetbrains:kotlin-css-jvm:1.0.0-pre.58-kotlin-1.3.0') {
            exclude module: 'kotlin-runtime'
            exclude module: 'kotlin-reflect'
            exclude module: 'kotlin-stdlib'
            exclude module: 'kotlin-stdlib-common'
            exclude module: 'kotlin-stdlib-jdk8'
        }
    }
}


intellij {
    if (project.hasProperty("customSinceBuild")) {
        patchPluginXml.sinceBuild = customSinceBuild
        patchPluginXml.untilBuild = customUntilBuild
    }
    patchPluginXml.changeNotes file("changes.html").getText()
    patchPluginXml.pluginDescription file("description.html").getText()
    pluginName 'EduTools'
    updateSinceUntilBuild true
    downloadSources false
    def pluginsList = ["PythonCore:$pythonPluginVersion", "org.rust.lang:$rustPluginVersion"]
    if (isJvmBasedIDE) {
        pluginsList += ['junit', 'Kotlin', "org.intellij.scala:$scalaPluginVersion"]
    }
    if (baseIDE == "idea") {
        pluginsList += ["NodeJS:$nodeJsPluginVersion", "JavaScriptLanguage"]
    }
    
    plugins pluginsList.toArray()
}

task configurePyCharm {
    doLast {
        if (!project.hasProperty("pycharmPath")) {
            throw new InvalidUserDataException("Path to PyCharm installed locally is needed\nDefine \"pycharmPath\" property")
        }
        intellij.sandboxDirectory pycharmSandbox
        intellij.alternativeIdePath pycharmPath
    }
}

task configureWebStorm {
    doLast {
        if (!project.hasProperty("webStormPath")) {
            throw new InvalidUserDataException("Path to WebStorm installed locally is needed\nDefine \"webStormPath\" property")
        }
        intellij.sandboxDirectory webStormSandbox
        intellij.alternativeIdePath webStormPath
    }
}

task configureCLion {
    doLast {
        if (project.hasProperty("clionPath")) {
            intellij.alternativeIdePath clionPath
        }
        intellij.sandboxDirectory clionSandbox
        
    }
}

task configureAndroidStudio {
    doLast {
        if (project.hasProperty("androidStudioPath")) {
            intellij.alternativeIdePath androidStudioPath
        }
        intellij.sandboxDirectory studioSandbox
    }
}

task copyXmls(type: Copy) {
    def resultingMetaInf = "${sourceSets.main.output.resourcesDir}/META-INF"

    for (def subProject : project.subprojects) {
        from "${subProject.name}/resources/META-INF"
        into resultingMetaInf
        include "*.xml"
    }
}

jar.dependsOn(copyXmls)

task removeIncompatiblePlugins(type: Delete) {
    doLast {
        deletePlugin(pycharmSandbox, "python-ce")
        deletePlugin(clionSandbox, "python-ce")
        deletePlugin(pycharmSandbox, "Scala")
    }
}

def deletePlugin(String sandboxPath, String pluginName) {
    file(sandboxPath + File.separator + "plugins" + File.separator + pluginName).deleteDir()    
}

// we need this so as not to install incompatible plugins.
// for example, python and Scala plugins on PyCharm
prepareSandbox.finalizedBy(removeIncompatiblePlugins)

subprojects {
    sourceSets {
        main {
            java.srcDirs 'src', "branches/$environmentName/src"
            resources.srcDirs 'resources', "branches/$environmentName/resources"
            kotlin.srcDirs 'src', "branches/$environmentName/src"
        }

        test {
            java.srcDirs 'testSrc', "branches/$environmentName/testSrc"
            resources.srcDirs 'testResources', "branches/$environmentName/testResources"
            kotlin.srcDirs 'testSrc', "branches/$environmentName/testSrc"
        }
    }

    project.tasks.getByPath("runIde").enabled false
    project.tasks.getByPath("prepareSandbox").enabled false
}

sourceSets {
    main {
        resources.srcDirs 'resources'
    }
}

project(':educational-core') {

    task downloadColorFile(type: Download) {
        overwrite false
        src 'https://raw.githubusercontent.com/ozh/github-colors/master/colors.json'
        dest "${projectDir}/resources/languageColors/colors.json"
    }
}

project(':jvm-core') {
    intellij {
        if (!isJvmBasedIDE) {
            localPath = null
            version = ideaVersion
        }
        plugins 'junit', 'properties', 'gradle', 'Groovy'
    }
    
    dependencies {
        compile project(':educational-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Java') {
    intellij {
        localPath = null
        version ideaVersion
        plugins 'junit', 'properties', 'gradle', 'Groovy'
    }

    dependencies {
        compile project(':educational-core')
        compile project(':jvm-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
        testCompile project(':jvm-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Kotlin') {
    intellij {
        if (!isJvmBasedIDE) {
            localPath = null
            version = ideaVersion
        }
        plugins 'Kotlin', 'junit', 'properties', 'gradle', 'Groovy'
    }

    dependencies {
        compile project(':educational-core')
        compile project(':jvm-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
        testCompile project(':jvm-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Scala') {
    intellij {
        localPath = null
        version ideaVersion
        plugins "org.intellij.scala:$scalaPluginVersion", 'junit', 'properties', 'gradle', 'Groovy'
    }

    dependencies {
        compile project(':educational-core')
        compile project(':jvm-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
        testCompile project(':jvm-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Android') {
    intellij {
        localPath studioPath()
        plugins 'android', 'junit', 'properties', 'gradle', 'Groovy', 'IntelliLang', 'smali'
    }

    dependencies {
        compile project(':educational-core')
        compile project(':jvm-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
        testCompile project(':jvm-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Python') {
    intellij {
        // FIXME we should compile python module with CLion too
        if (!isJvmBasedIDE) {
            localPath = null
            version = ideaVersion
        }
        plugins "PythonCore:$pythonPluginVersion"
    }

    dependencies {
        compile project(':educational-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-JavaScript') {
    intellij {
        localPath = null
        version ideaVersion
        plugins "NodeJS:$nodeJsPluginVersion", "JavaScriptLanguage", "CSS", "JavaScriptDebugger"
    }
    dependencies {
        compile project(':educational-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Rust') {
    intellij {
        plugins "org.rust.lang:$rustPluginVersion"
    }

    dependencies {
        compile project(':educational-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs
    }
}

project(':Edu-Cpp') {
    intellij {
        localPath = null
        version clionVersion
    }

    dependencies {
        compile project(':educational-core')
        testCompile project(':educational-core').sourceSets.test.output.classesDirs 
    }
}


runIde.systemProperty("-Didea.is.internal", "true")
runIde.systemProperty("-ea", "")

dependencies {
    compile project(':educational-core'), 
            project(':jvm-core'), 
            project(':Edu-Python'), 
            project(':Edu-Kotlin'), 
            project(':Edu-Java'),
            project(':Edu-Scala'), 
            project(':Edu-Android'),
            project(':Edu-JavaScript'),
            project(':Edu-Rust'),
            project(':Edu-Cpp')
}

idea {
    project {
        jdkName = 1.8
        languageLevel = 1.8
        vcs = 'Git'
    }
}

allprojects {
    test {
        if (rootProject.hasProperty("stepikTestClientSecret")) {
            environment 'STEPIK_TEST_CLIENT_SECRET', stepikTestClientSecret
        }

        if (rootProject.hasProperty("stepikTestClientId")) {
            environment 'STEPIK_TEST_CLIENT_ID', stepikTestClientId
        }
        if (project.hasProperty('excludeTests')) {
            exclude project.property('excludeTests')
        }

        ignoreFailures true
    }
}