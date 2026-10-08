plugins { `java-library` }
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
dependencies {
    compileOnly("androidx.annotation:annotation:1.9.1")
    compileOnly("com.google.errorprone:error_prone_annotations:2.36.0")
}
