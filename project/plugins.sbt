/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0") // for maintenance of copyright file header
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.7.0")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")

// for releasing
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32")
addSbtPlugin("com.github.pjfanning" % "sbt-pekko-build" % "0.3.3")
addSbtPlugin("com.github.pjfanning" % "sbt-source-dist" % "0.1.12")
addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.6.1")

//// docs
addSbtPlugin("org.apache.pekko" % "pekko-sbt-paradox" % "1.0.1")
addSbtPlugin(("com.github.sbt" % "sbt-site-paradox" % "1.5.0").excludeAll(
  "com.lightbend.paradox", "sbt-paradox"))
