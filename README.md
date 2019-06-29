AppVeyor REST API Client (from Swagger, in Java)
================================================

[![Build Status](https://ci.appveyor.com/api/projects/status/l7e72uc1hiwycwg0/branch/master?svg=true)](https://ci.appveyor.com/project/kevinoid/appveyor-swagger-java)

[AppVeyor REST API](https://www.appveyor.com/docs/api/) client generated from
[the unofficial Swagger
definition](https://github.com/kevinoid/appveyor-swagger/) in Java.  The
primary purpose of this project is to serve as a demonstration and test of the
Swagger definition using the Java language.  It is also suitable for use as a
library to access the AppVeyor API from languages which run on the JVM.  Users
should be aware that the generated classes are a bit clunky and the API is
_likely to change frequently_ as a result of updates to
[swagger-codegen](https://github.com/swagger-api/swagger-codegen) or the REST
API.

## Introductory Example

```java
import name.kevinlocke.appveyor.ApiClient;
import name.kevinlocke.appveyor.ApiException;
import name.kevinlocke.appveyor.api.ProjectApi;
import name.kevinlocke.appveyor.model.Build;

public class AppveyorApiExample {
    public static void main(String[] args) {
        ApiClient apiClient = new ApiClient();
        apiClient.setApiKeyPrefix("Bearer");
        // API Token from https://ci.appveyor.com/api-token
        apiClient.setApiKey(apiToken);

        ProjectApi projectApi = new ProjectApi(apiClient);
        try {
            Build build =
                // Account name and slug from AppVeyor project URL
                projectApi.getProjectLastBuild(accountName, projectSlug)
                    .getBuild();
            System.out.println("Last build status: " + build.getStatus());
        } catch (ApiException e) {
            System.err.println("Error getting last build: " + e.getMessage());
        }
    }
}
```

## Usage

### Maven

To use this project as a dependency with [Maven](https://maven.apache.org),
add the following dependency to your project's `pom.xml`:

```xml
<dependency>
  <groupId>name.kevinlocke.appveyor</groupId>
  <artifactId>appveyor-swagger</artifactId>
  <version>0.1.0</version>
</dependency>
```

### sbt

To use this project as a dependency with [sbt](http://www.scala-sbt.org)
add the following dependency to your project's `build.sbt`:

```scala
libraryDependencies += "name.kevinlocke.appveyor" % "appveyor-swagger" % "0.1.0"
```

### Gradle

To use this project as a dependency with [Gradle](https://gradle.org/),
add the following dependency to your project's `build.gradle`:

```groovy
compile "name.kevinlocke.appveyor:appveyor-swagger:0.1.0"
```

### Others

To use this project as a dependency of another build system, a
[JAR](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/index.html)
can be created by running the following commands:

```sh
git clone --branch v0.1.0 https://github.com/kevinoid/appveyor-swagger-java.git
cd appveyor-swagger-java
mvn package
```

The generated JAR file will be at `target/appveyor-swagger-0.1.0.jar`.

## Documentation

The [project documentation](https://kevinoid.github.io/appveyor-swagger-java/)
includes [Javadoc](https://kevinoid.github.io/appveyor-swagger-java/apidocs/)
and [Swagger-generated endpoint and model
documentation](https://kevinoid.github.io/appveyor-swagger-java/swaggerdocs/#Documentation_for_API_Endpoints).
There is also [Swagger API
Documentation](https://kevinoid.github.io/appveyor-swagger).


## Development in Eclipse

Developers who prefer to use Eclipse to work on this project can use the
following steps to set up the project on their machine:

1.  [Install the M2Eclipse plugin.](https://www.eclipse.org/m2e/)
2.  Import the project (from **File > Import...**) using either **Check out
    Maven Projects from SCM** (if this repo is already cloned locally) or
    **Existing Maven Projects** (if this repo is not yet cloned locally).
    (Ignore "Plugin execution not covered by lifecycle configuration"
    warnings.)
3.  Run `mvn process-sources` in the project directory (outside of Eclipse).
4.  Add `target/generated-sources/openapi/src/main/java` to the build path as
    a source folder (e.g. in the Eclipse Package Explorer window, right-click
    the folder and choose **Build Path > Use as Source Folder**).
5.  (Optional) Set **Ignore optional compile problems: Yes** on
    `target/generated-sources/openapi/src/main/java` in the Build
    Path configuration (since the code is automatically generated and requires
    changes in `openapi-generator` to fix).
6.  (Optional) Configure **Java Code Style > Formatter** in the project
    properties to use the profile from `.eclipse-formatter.xml`.
7.  (Optional) [Format source code on
    save](https://stackoverflow.com/a/234625).

## Testing

Running the tests defined in this project requires an AppVeyor account.  The
[API Token](https://ci.appveyor.com/api-token) must be set in the environment
variable `$APPVEYOR_API_TOKEN` when running the tests.  This can be
accomplished as follows:

```sh
APPVEYOR_API_TOKEN=deadbeef mvn test
```

## License

This library is available under the terms of the
[MIT License](https://opensource.org/licenses/MIT).
