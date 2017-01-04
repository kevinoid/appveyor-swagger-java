AppVeyor REST API Client (from Swagger, in Java)
================================================

[![Build Status: Linux](https://img.shields.io/travis/kevinoid/appveyor-swagger-java.svg?style=flat&amp;label=build+on+linux)](https://travis-ci.org/kevinoid/appveyor-swagger-java)
[![Build Status: Windows](https://img.shields.io/appveyor/ci/kevinoid/appveyor-swagger-java.svg?style=flat&amp;label=build+on+windows)](https://ci.appveyor.com/project/kevinoid/appveyor-swagger-java)
[![Coverage](https://img.shields.io/codecov/c/github/kevinoid/appveyor-swagger-java.svg?style=flat)](https://codecov.io/github/kevinoid/appveyor-swagger-java?branch=master)

AppVeyor REST API Client generated from [the unofficial Swagger
definition](https://github.com/kevinoid/appveyor-swagger/) in Java.  The
primary purpose of this project is to serve as a demonstration and test of the
Swagger definition using the Java language.  It is also suitable for use as a
library to access the AppVeyor API from languages which run on the JVM.  Users
should be aware that the generated classes are a bit clunky and the API is
_likely to change frequently_ as a result of updates to
[swagger-codegen](https://github.com/swagger-api/swagger-codegen).

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

## License

This library is available under the terms of the
[MIT License](https://opensource.org/licenses/MIT).
