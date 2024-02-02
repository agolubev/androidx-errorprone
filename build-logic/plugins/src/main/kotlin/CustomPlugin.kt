/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.exclude
import org.gradle.process.CommandLineArgumentProvider

private const val ERROR_PRONE_CONFIGURATION = "errorprone"
private const val ERROR_PRONE_VERSION = "com.google.errorprone:error_prone_core:2.14.0"
private const val ERROR_PRONE_VARIANT = "Release"

class CustomPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.afterEvaluate {
            // delay initialization until after AGP's afterEvaluate returns,
            // so we can guarantee the error prone task is registered after the java compile task is
            project.makeErrorProneTask()
        }
    }
}

private fun Project.createErrorProneConfiguration(): Configuration {
    val errorProneConfiguration =
        configurations.create(ERROR_PRONE_CONFIGURATION) {
            it.isVisible = false
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.exclude(group = "com.google.errorprone", module = "javac")
        }
    dependencies.add(ERROR_PRONE_CONFIGURATION, ERROR_PRONE_VERSION)
    return errorProneConfiguration
}

private fun Project.makeErrorProneTask() {
    maybeRegister<JavaCompile>(
        name = "runErrorProne",
        onConfigure = {
            // main logic is placed here to have action configuration happens after
            // Gradle configure phase. So if you run task that does not trigger `errorProne` -
            // this call back will not be executed
            val compileTask =
                tasks.withType(JavaCompile::class.java)
                    // rely on AGP task name as it fairly stable and gives access to all
                    // task attributes once it configures
                    .named("compile${ERROR_PRONE_VARIANT}JavaWithJavac")
                    .get() // will throw illegal state if task is not there

            val config = createErrorProneConfiguration()
            it.classpath = compileTask.classpath
            it.source = compileTask.source
            it.destinationDirectory.set(layout.buildDirectory.dir("errorProne"))
            it.options.compilerArgumentProviders += compileTask.options.compilerArgumentProviders
            it.options.annotationProcessorPath =
                compileTask.options.annotationProcessorPath?.let { collection -> collection + config }
                    ?: config
            it.options.bootstrapClasspath = compileTask.options.bootstrapClasspath
            it.sourceCompatibility = compileTask.sourceCompatibility
            it.targetCompatibility = compileTask.targetCompatibility
            it.configureWithErrorProne()

            it.dependsOn(compileTask.dependsOn)
        },
        onRegister = { errorProneProvider ->
            tasks.named("check").configure { it.dependsOn(errorProneProvider) }
        }
    )
}


private fun JavaCompile.configureWithErrorProne() {
    options.isFork = true
    options.forkOptions.jvmArgs!!.addAll(
        listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        )
    )
    val compilerArgs = this.options.compilerArgs
    compilerArgs +=
        listOf(
            // Tell error-prone that we are running it on android compatible libraries
            "-XDandroidCompatible=true",
            "-XDcompilePolicy=simple", // Workaround for b/36098770
            listOf(
                "-Xplugin:ErrorProne",

                // Ignore intermediate build output, generated files, and external sources. Also
                // sources
                // imported from Android Studio and IntelliJ which are used in the lint-checks
                // project.
                "-XepExcludedPaths:.*/(build/generated|build/errorProne|external|" +
                        "compileTransaction/compile-output|" +
                        "lint-checks/src/main/java/androidx/com)/.*",

                // Consider re-enabling the following checks. Disabled as part of
                // error-prone upgrade
                "-Xep:InlineMeSuggester:OFF",
                "-Xep:NarrowCalculation:OFF",
                "-Xep:LongDoubleConversion:OFF",
                "-Xep:UnicodeEscape:OFF",
                "-Xep:JavaUtilDate:OFF",
                "-Xep:UnrecognisedJavadocTag:OFF",
                "-Xep:ObjectEqualsForPrimitives:OFF",
                "-Xep:DoNotCallSuggester:OFF",
                "-Xep:EqualsNull:OFF",
                "-Xep:MalformedInlineTag:OFF",
                "-Xep:MissingSuperCall:OFF",
                "-Xep:ToStringReturnsNull:OFF",
                "-Xep:ReturnValueIgnored:OFF",
                "-Xep:MissingImplementsComparable:OFF",
                "-Xep:EmptyTopLevelDeclaration:OFF",
                "-Xep:InvalidThrowsLink:OFF",
                "-Xep:StaticAssignmentOfThrowable:OFF",
                "-Xep:DoNotClaimAnnotations:OFF",
                "-Xep:AlreadyChecked:OFF",
                "-Xep:StringSplitter:OFF",

                // We allow inter library RestrictTo usage.
                "-Xep:RestrictTo:OFF",

                // Disable the following checks.
                "-Xep:UnescapedEntity:OFF",
                "-Xep:MissingSummary:OFF",
                "-Xep:StaticAssignmentInConstructor:OFF",
                "-Xep:InvalidLink:OFF",
                "-Xep:InvalidInlineTag:OFF",
                "-Xep:EmptyBlockTag:OFF",
                "-Xep:EmptyCatch:OFF",
                "-Xep:JdkObsolete:OFF",
                "-Xep:PublicConstructorForAbstractClass:OFF",
                "-Xep:MutablePublicArray:OFF",
                "-Xep:NonCanonicalType:OFF",
                "-Xep:ModifyCollectionInEnhancedForLoop:OFF",
                "-Xep:InheritDoc:OFF",
                "-Xep:InvalidParam:OFF",
                "-Xep:InlineFormatString:OFF",
                "-Xep:InvalidBlockTag:OFF",
                "-Xep:ProtectedMembersInFinalClass:OFF",
                "-Xep:SameNameButDifferent:OFF",
                "-Xep:AnnotateFormatMethod:OFF",
                "-Xep:ReturnFromVoid:OFF",
                "-Xep:AlmostJavadoc:OFF",
                "-Xep:InjectScopeAnnotationOnInterfaceOrAbstractClass:OFF",
                "-Xep:InvalidThrows:OFF",

                // Disable checks which are already enforced by lint.
                "-Xep:PrivateConstructorForUtilityClass:OFF",

                // Enforce the following checks.
                "-Xep:JavaTimeDefaultTimeZone:ERROR",
                "-Xep:ParameterNotNullable:ERROR",
                "-Xep:MissingOverride:ERROR",
                "-Xep:EqualsHashCode:ERROR",
                "-Xep:NarrowingCompoundAssignment:ERROR",
                "-Xep:ClassNewInstance:ERROR",
                "-Xep:ClassCanBeStatic:ERROR",
                "-Xep:SynchronizeOnNonFinalField:ERROR",
                "-Xep:OperatorPrecedence:ERROR",
                "-Xep:IntLongMath:ERROR",
                "-Xep:MissingFail:ERROR",
                "-Xep:JavaLangClash:ERROR",
                "-Xep:TypeParameterUnusedInFormals:ERROR",
                // "-Xep:StringSplitter:ERROR", // disabled with upgrade to 2.14.0
                "-Xep:ReferenceEquality:ERROR",
                "-Xep:AssertionFailureIgnored:ERROR",
                "-Xep:UnnecessaryParentheses:ERROR",
                "-Xep:EqualsGetClass:ERROR",
                "-Xep:UnusedVariable:ERROR",
                "-Xep:UnusedMethod:ERROR",
                "-Xep:UndefinedEquals:ERROR",
                "-Xep:ThreadLocalUsage:ERROR",
                "-Xep:FutureReturnValueIgnored:ERROR",
                "-Xep:ArgumentSelectionDefectChecker:ERROR",
                "-Xep:HidingField:ERROR",
                "-Xep:UnsynchronizedOverridesSynchronized:ERROR",
                "-Xep:Finally:ERROR",
                "-Xep:ThreadPriorityCheck:ERROR",
                "-Xep:AutoValueFinalMethods:ERROR",
                "-Xep:ImmutableEnumChecker:ERROR",
                "-Xep:UnsafeReflectiveConstructionCast:ERROR",
                "-Xep:LockNotBeforeTry:ERROR",
                "-Xep:DoubleCheckedLocking:ERROR",
                "-Xep:InconsistentCapitalization:ERROR",
                "-Xep:ModifiedButNotUsed:ERROR",
                "-Xep:AmbiguousMethodReference:ERROR",
                "-Xep:EqualsIncompatibleType:ERROR",
                "-Xep:ParameterName:ERROR",
                "-Xep:RxReturnValueIgnored:ERROR",
                "-Xep:BadImport:ERROR",
                "-Xep:MissingCasesInEnumSwitch:ERROR",
                "-Xep:ObjectToString:ERROR",
                "-Xep:CatchAndPrintStackTrace:ERROR",
                "-Xep:MixedMutabilityReturnType:ERROR",

                // Nullaway
                "-XepIgnoreUnknownCheckNames", // https://github.com/uber/NullAway/issues/25
                "-Xep:NullAway:ERROR",
                "-XepOpt:NullAway:AnnotatedPackages=android.arch,android.support,androidx"
            )
                .joinToString(" ")
        )
}

inline fun <reified T : Task> Project.maybeRegister(
    name: String,
    crossinline onConfigure: (T) -> Unit,
    crossinline onRegister: (TaskProvider<T>) -> Unit
): TaskProvider<T> {
    @Suppress("UNCHECKED_CAST")
    return tasks.register(name, T::class.java) { onConfigure(it) }.also(onRegister)
}

class CommandLineArgumentProviderAdapter(@get:Input val arguments: Provider<Map<String, String>>) :
    CommandLineArgumentProvider {
    override fun asArguments(): MutableIterable<String> {
        return mutableListOf<String>().also {
            for ((key, value) in arguments.get()) {
                it.add("-A$key=$value")
            }
        }
    }
}