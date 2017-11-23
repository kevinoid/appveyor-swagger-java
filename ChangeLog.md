v0.2.10 / 2017-11-23
====================

  * Updated appveyor-swagger definition to v0.20171123.0.
    - Add `getBuildArtifacts` operation and `ArtifactModel` schema for response.
    - Add `getBuildArtifact` operation.
    - Add `getProjectArtifact` operation.
    - Add `tags` property to `DeploymentEnvironment`.
    - Add `isPrivateProject` property to `NuGetFeed`.
    - Add enumeration values to `ArtifactType` based on `Push-AppveyorArtifact`
      cmdlet on build workers.
    - Remove `name` property requirement from `Artifact`.
  * Test new `getProjectArtifact` method.
  * Test new `getBuildArtifact` method.
  * Test new `getBuildArtifacts` method.
  * Update swagger-request-validator to 1.3.2.

v0.2.9 / 2017-10-31
===================

  * Updated appveyor-swagger definition to v0.20171031.0.
    - Add `getProjectEnvironmentVariables` operation.
    - Add `updateProjectEnvironmentVariables` operation.
  * Test new `ProjectApi.getProjectEnvironmentVariables` operation.
  * Test new `ProjectApi.updateProjectEnvironmentVariables` operation.
  * Update swagger-request-validator to 1.3.0

v0.2.8 / 2017-10-23
===================

  * Updated appveyor-swagger definition to v0.20171023.0.
    - Add `dotnetCsprojAssemblyVersionFormat` to `ProjectConfiguration`.
    - Add `dotnetCsprojFileVersionFormat` to `ProjectConfiguration`.
    - Add `dotnetCsprojInformationalVersionFormat` to `ProjectConfiguration`.

v0.2.7 / 2017-10-21
===================

  * Updated appveyor-swagger definition to v0.20171021.0.
    - Add `pro-vs2013` to `BuildCloudName`.
  * Update swagger-request-validator to 1.2.3
  * Update gson to 2.8.2

v0.2.6 / 2017-10-19
===================

  * Updated appveyor-swagger definition to v0.20171019.0.
    - Add `pro-vs2017` to `BuildCloudName`.
  * Exclude test counts from build job comparison

v0.2.5 / 2017-08-27
===================

  * Updated appveyor-swagger definition to v0.20170827.0.
    - Add `saveBuildCacheInPullRequests` to `Project`.

v0.2.4 / 2017-08-03
===================

  * Updated appveyor-swagger definition to v0.20170803.0.
    - Add `dotnetCsprojFile`, `dotnetCsprojPackageVersionFormat`,
      `dotnetCsprojVersionFormat`, `hotFixScripts` to `ProjectConfiguration`.
  * Minor updates for other dependencies.

v0.2.3 / 2017-06-22
===================

  * Updated appveyor-swagger definition to v0.20170622.0.
    - Add `Visual Studio 2017 Preview` to `BuildWorkerImageName`

v0.2.2 / 2017-05-18
===================

  * Updated appveyor-swagger definition.
    - Add `Ubuntu` to `OSType`, `BuildCloudName`, `BuildWorkerImageName`

v0.2.1 / 2017-05-07
===================

  * Updated appveyor-swagger definition.
    - Add `osType` to `BuildJob` and `BuildWorkerImage`.
    - Add `rollingBuildsDoNotCancelRunningBuilds` to `Project`.
    - Better document `scheduleCrontabExpression`.

v0.2.0 / 2017-05-04
===================

  * Updated appveyor-swagger definition.
    - **Breaking**  Rename path parameter for `getProjectStatusBadge` and
      `getProjectBranchStatusBadge` to `statusBadgeId` to match its new source,
      which is the new `statusBadgeId` property of `ProjectWithConfiguration`
      objects.  The value of this property matches `webhookId` (the previous
      parameter source) for existing projects but will differ for new projects.
    - Add enumeration values for `BuildCloudName` and `BuildWorkerImageName`.
    - Add `matrixExclude` property to `ProjectConfiguration`.
  * Use statusBadgeId in status badge queries

v0.1.0 / 2017-03-09
===================

  * Initial public release.
