lazy val root = (project in file("."))
  .settings(
    name := "zuora-invoice-write-offs",
    scalaVersion := "2.12.3",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.7",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
    libraryDependencies += "com.typesafe.play" % "play-json_2.12" % "2.6.0",
    libraryDependencies += "com.github.melrief" %% "purecsv" % "0.1.0",
    libraryDependencies += "com.squareup.okhttp3" % "okhttp" % "4.9.2",
    libraryDependencies += "org.scalaz" % "scalaz-core_2.12" % "7.2.15"
  )
