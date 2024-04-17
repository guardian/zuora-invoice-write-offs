lazy val root = (project in file("."))
  .settings(
    name := "zuora-invoice-write-offs",
    scalaVersion := "2.13.10",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.13",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    libraryDependencies += "io.kontainers" %% "purecsv" % "1.3.10",
    libraryDependencies += "com.squareup.okhttp3" % "okhttp" % "4.10.0",
    libraryDependencies += "org.scalaz" % "scalaz-core_2.12" % "7.2.15",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.4",
    dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2", // Fixes Snyk vulnerability alert
  )
