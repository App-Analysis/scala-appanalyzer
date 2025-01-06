name := "appanalyzer"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / versionScheme := Some("semver-spec")
organization := "de.halcony"

enablePlugins(JavaAppPackaging)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,
  "de.halcony" %% "scala-argparse" % "1.1.11",
  "io.spray" %% "spray-json" % "1.3.6",
  "com.typesafe" % "config" % "1.4.2",
  "org.wvlet.airframe" %% "airframe-log" % "23.3.0",
  "org.slf4j" % "slf4j-nop" % "2.0.5",
  "me.tongfei" % "progressbar" % "0.9.5",
  "io.appium" % "java-client" % "9.2.2",
  "commons-io" % "commons-io" % "2.16.1",
  "org.scalikejdbc" %% "scalikejdbc" % "4.0.0",
  "org.postgresql" % "postgresql" % "42.5.4",
  "com.mchange" % "mchange-commons-java" % "0.2.20",
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.clapper" %% "classutil" % "1.5.1",
  "javax.mail" % "mail" % "1.4.7",
)

ThisBuild / resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.mavenCentral,
  "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/public",
  "Apache public" at "https://repository.apache.org/content/groups/public/"
)

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  // "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
  //"-Xlint:nullary-override",           // Warn when non-nullary def f() overrides nullary def f.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals", // Warn if a local definition is unused.
  "-Ywarn-unused:params", // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates" // Warn if a private member is unused.
  // "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
)

compile / javacOptions ++= Seq("-Xlint:all", "-Xlint:-cast", "-g")
Test / fork := false
run / fork := true
cancelable in Global := true
Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v")

checkstyleConfigLocation := CheckstyleConfigLocation.File(
  "config/checkstyle/google_checks.xml")
checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Info)

sonatypeProfileName := "de.halcony"
// this is required for sonatype sync requirements
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/simkoc/scala-plotalyzer"),
    "scm:git@github.com:simkoc/scala-plotalyzer.git"
  )
)
// this is required for sonatype sync requirements
ThisBuild / developers := List(
  Developer(
    id = "simkoc",
    name = "Simon Koch",
    email = "ossrh@halcony.de",
    url = url("https://github.com/simkoc/")
  )
)
// this is required for sonatype sync requirements
ThisBuild / licenses := List(
  "MIT" -> url(
    "https://github.com/simkoc/scala-appanalyzer/blob/master/LICENSE"))
// this is required for sonatype sync requirements
ThisBuild / homepage := Some(url("https://github.com/simkoc/scala-appanalyzer"))

// below is pretty much cargo cult/fuzzing....
import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseVersionBump := sbtrelease.Version.Bump.Bugfix
publishTo := sonatypePublishToBundle.value
releaseProcess := Seq[ReleaseStep](
  runClean,
  runTest,
  inquireVersions,
  setReleaseVersion,
  commitReleaseVersion,
  publishArtifacts,
  releaseStepCommand("publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges,
)
