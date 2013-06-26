name := "sandbox"

scalaVersion := "2.9.0-1"

libraryDependencies ++=
    Seq("org.apache.httpcomponents" % "httpclient" % "4.2.1",
     "com.ning" % "async-http-client" % "1.7.12")

libraryDependencies ++= Seq("core", "http", "mime") map {
  (component: String) => "net.databinder" %% ("dispatch-" + component) % "0.8.5"
}
