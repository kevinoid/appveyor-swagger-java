
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
