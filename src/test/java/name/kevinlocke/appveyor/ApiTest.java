package name.kevinlocke.appveyor;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterGroups;
import org.testng.annotations.Test;

import name.kevinlocke.appveyor.api.BuildApi;
import name.kevinlocke.appveyor.api.CollaboratorApi;
import name.kevinlocke.appveyor.api.DeploymentApi;
import name.kevinlocke.appveyor.api.EnvironmentApi;
import name.kevinlocke.appveyor.api.ProjectApi;
import name.kevinlocke.appveyor.api.RoleApi;
import name.kevinlocke.appveyor.api.UserApi;
import name.kevinlocke.appveyor.model.Build;
import name.kevinlocke.appveyor.model.BuildMode;
import name.kevinlocke.appveyor.model.BuildStartRequest;
import name.kevinlocke.appveyor.model.CollaboratorAddition;
import name.kevinlocke.appveyor.model.CollaboratorUpdate;
import name.kevinlocke.appveyor.model.Deployment;
import name.kevinlocke.appveyor.model.DeploymentEnvironment;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentAddition;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentDeployment;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentDeploymentsResults;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentSettings;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentSettingsResults;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentWithSettings;
import name.kevinlocke.appveyor.model.DeploymentProviderType;
import name.kevinlocke.appveyor.model.DeploymentStartRequest;
import name.kevinlocke.appveyor.model.Project;
import name.kevinlocke.appveyor.model.ProjectAddition;
import name.kevinlocke.appveyor.model.ProjectBuildNumberUpdate;
import name.kevinlocke.appveyor.model.ProjectBuildResults;
import name.kevinlocke.appveyor.model.ProjectConfiguration;
import name.kevinlocke.appveyor.model.ProjectDeployment;
import name.kevinlocke.appveyor.model.ProjectDeploymentsResults;
import name.kevinlocke.appveyor.model.ProjectHistory;
import name.kevinlocke.appveyor.model.ProjectSettingsResults;
import name.kevinlocke.appveyor.model.ProjectWithConfiguration;
import name.kevinlocke.appveyor.model.RepositoryProvider;
import name.kevinlocke.appveyor.model.Role;
import name.kevinlocke.appveyor.model.RoleAddition;
import name.kevinlocke.appveyor.model.RoleWithGroups;
import name.kevinlocke.appveyor.model.Script;
import name.kevinlocke.appveyor.model.ScriptLanguage;
import name.kevinlocke.appveyor.model.Status;
import name.kevinlocke.appveyor.model.StoredNameValue;
import name.kevinlocke.appveyor.model.StoredValue;
import name.kevinlocke.appveyor.model.TestMode;
import name.kevinlocke.appveyor.model.UserAccount;
import name.kevinlocke.appveyor.model.UserAccountRolesResults;
import name.kevinlocke.appveyor.model.UserAddition;
import name.kevinlocke.appveyor.testutils.TestApiClient;

/**
 * Tests for the AppVeyor API client.
 *
 * This class should be split into smaller classes, probably based on testing
 * each *Api class separately. However, it is difficult to capture the API state
 * dependencies since TestNG doesn't support cross-class method dependencies and
 * requires data providers to either be in the same class or static. Group
 * dependencies can be used, but are not reliable for running single tests
 * https://stackoverflow.com/q/15762998 So for now the tests are all in this
 * class.
 */
public class ApiTest {
	// Must exist and can not be created by the account used for testing
	public static final String TEST_COLLABORATOR_EMAIL = "kevin@kevinlocke.name";
	public static final String TEST_COLLABORATOR_ROLE_NAME = "User";
	public static final String TEST_ENVIRONMENT_NAME = "Test Env";
	public static final Integer TEST_PROJECT_BUILD_NUMBER = 45;
	public static final String TEST_PROJECT_BUILD_SCRIPT = "echo Build";
	public static final String TEST_PROJECT_BRANCH = "master";
	// Note: Using GitHub provider requires GitHub auth to AppVeyor test user
	public static final RepositoryProvider TEST_PROJECT_REPO_PROVIDER = RepositoryProvider.GIT;
	public static final String TEST_PROJECT_REPO_NAME = "https://github.com/kevinoid/empty.git";
	public static final String TEST_PROJECT_TEST_SCRIPT = "echo Test";
	public static final String TEST_ROLE_NAME = "Test Role";
	public static final String TEST_USER_EMAIL = "bob2@example.com";
	public static final String TEST_USER_NAME = "Test User";
	public static final String TEST_USER_ROLE_NAME = "User";

	/** Assert that two roles are the same, excluding mutable fields. */
	public static void assertSameRole(RoleWithGroups actual,
			RoleWithGroups expected) {
		assertEquals(actual.getRoleId(), expected.getRoleId());
		assertEquals(actual.getName(), expected.getName());
		assertEquals(actual.getIsSystem(), expected.getIsSystem());
		assertEquals(actual.getCreated(), expected.getCreated());
	}

	/** Assert that two roles are the same, excluding mutable fields. */
	public static void assertSameRole(Role actual, RoleWithGroups expected) {
		assertEquals(actual.getRoleId(), expected.getRoleId());
		assertEquals(actual.getName(), expected.getName());
		assertEquals(actual.getIsSystem(), expected.getIsSystem());
		assertEquals(actual.getCreated(), expected.getCreated());
	}

	protected final ApiClient apiClient;
	protected final BuildApi buildApi;
	protected final CollaboratorApi collaboratorApi;
	protected final DeploymentApi deploymentApi;
	protected final EnvironmentApi environmentApi;
	protected final ProjectApi projectApi;
	protected final RoleApi roleApi;
	protected final UserApi userApi;

	private volatile Map<String, Role> systemRolesByName = null;

	protected volatile Build testBuild;
	protected volatile UserAccount testCollaborator;
	protected volatile Role testCollaboratorRole;
	protected volatile Deployment testDeployment;
	protected volatile DeploymentEnvironmentWithSettings testEnvironment;
	protected volatile Project testProject;
	protected volatile ProjectWithConfiguration testProjectConfig;
	protected volatile String testProjectYaml;
	protected volatile RoleWithGroups testRole;
	protected volatile UserAccount testUser;

	public ApiTest() {
		apiClient = TestApiClient.getTestApiClient();
		buildApi = new BuildApi(apiClient);
		collaboratorApi = new CollaboratorApi(apiClient);
		deploymentApi = new DeploymentApi(apiClient);
		environmentApi = new EnvironmentApi(apiClient);
		projectApi = new ProjectApi(apiClient);
		roleApi = new RoleApi(apiClient);
		userApi = new UserApi(apiClient);
	}

	@AfterGroups(groups = "role", alwaysRun = true)
	public void cleanupTestRole() throws ApiException {
		if (testRole != null) {
			roleApi.deleteRole(testRole.getRoleId());
			testRole = null;
		}
	}

	public void cleanupOldTestRoles() throws ApiException {
		Role oldTestRole = getRoleByName(TEST_ROLE_NAME);
		if (oldTestRole != null) {
			roleApi.deleteRole(oldTestRole.getRoleId());
		}
	}

	@Test(groups = "role")
	public void addRole() throws ApiException {
		RoleAddition roleAddition = new RoleAddition().name(TEST_ROLE_NAME);
		RoleWithGroups role;
		try {
			role = roleApi.addRole(roleAddition);
		} catch (ApiException e) {
			if (e.getResponseBody().indexOf("already exists") >= 0) {
				// Previous test run didn't clean up after itself, clean & retry
				cleanupOldTestRoles();
				role = roleApi.addRole(roleAddition);
			} else {
				throw e;
			}
		}

		testRole = role;
		assertEquals(role.getName(), TEST_ROLE_NAME);
	}

	@Test(dependsOnMethods = "addRole", groups = "role")
	public void addRoleDuplicate() {
		RoleAddition roleAddition = new RoleAddition().name(TEST_ROLE_NAME);
		try {
			roleApi.addRole(roleAddition);
			fail("Duplicate role added?");
		} catch (ApiException e) {
			assertTrue(e.getResponseBody().indexOf("already exists") >= 0);
		}
	}

	@Test(dependsOnMethods = "addRole", groups = "role")
	public void getRole() throws ApiException {
		RoleWithGroups gotRole = roleApi.getRole(testRole.getRoleId());
		assertSameRole(gotRole, testRole);
	}

	protected synchronized Role getRoleByName(String roleName)
			throws ApiException {
		Map<String, Role> rolesByName = systemRolesByName;
		if (rolesByName != null) {
			Role namedRole = rolesByName.get(roleName);
			if (namedRole != null) {
				return namedRole;
			}
		}

		List<Role> roles = roleApi.getRoles();

		if (rolesByName == null) {
			rolesByName = new HashMap<String, Role>(roles.size());
			for (Role role : roles) {
				if (role.getIsSystem()) {
					rolesByName.put(role.getName(), role);
				}
			}
			synchronized (this) {
				if (systemRolesByName == null) {
					systemRolesByName = rolesByName;
				}
			}
		}

		for (Role role : roles) {
			if (role.getName().equals(roleName)) {
				return role;
			}
		}
		return null;
	}

	@Test(dependsOnMethods = "addRole", groups = "role")
	public void getRoles() throws ApiException {
		Role testRoleByName = getRoleByName(TEST_ROLE_NAME);
		assertNotNull(testRoleByName, TEST_ROLE_NAME + " not in list!?");
		assertSameRole(testRoleByName, testRole);
	}

	@Test(dependsOnMethods = "addRole", groups = "role")
	public void updateRole() throws ApiException {
		RoleWithGroups updatedRole = roleApi.updateRole(testRole);
		assertNotNull(updatedRole);
		assertNotNull(updatedRole.getUpdated());
		assertSameRole(updatedRole, testRole);
	}

	@AfterGroups(groups = "user", alwaysRun = true)
	public void cleanupTestUser() throws ApiException {
		if (testUser != null) {
			userApi.deleteUser(testUser.getUserId());
			testUser = null;
		}
	}

	public void cleanupOldTestUsers() throws ApiException {
		for (UserAccount user : userApi.getUsers()) {
			if (user.getEmail().equals(TEST_USER_EMAIL)) {
				userApi.deleteUser(user.getUserId());
			}
		}
	}

	@Test(groups = "user")
	public void addUser() throws ApiException {
		UserAddition userAddition = new UserAddition();
		userAddition.setFullName(TEST_USER_NAME);
		userAddition.setEmail(TEST_USER_EMAIL);
		Role role = getRoleByName(TEST_USER_ROLE_NAME);
		userAddition.setRoleId(role.getRoleId());

		try {
			userApi.addUser(userAddition);
		} catch (ApiException e) {
			if (e.getResponseBody().indexOf("already exists") >= 0) {
				// Previous test run didn't clean up after itself, clean & retry
				cleanupOldTestUsers();
				userApi.addUser(userAddition);
			} else {
				throw e;
			}
		}
	}

	@Test(dependsOnMethods = "addUser", groups = "user")
	public void addUserDuplicate() throws ApiException {
		UserAddition userAddition = new UserAddition();
		userAddition.setFullName(TEST_USER_NAME);
		userAddition.setEmail(TEST_USER_EMAIL);
		Role role = getRoleByName(TEST_USER_ROLE_NAME);
		userAddition.setRoleId(role.getRoleId());
		try {
			userApi.addUser(userAddition);
			fail("Duplicate user added?");
		} catch (ApiException e) {
			assertTrue(e.getResponseBody().indexOf("already exists") >= 0);
		}
	}

	protected UserAccount getUserByEmail(String email) throws ApiException {
		List<UserAccount> users = userApi.getUsers();
		for (UserAccount user : users) {
			if (user.getEmail().equals(TEST_USER_EMAIL)) {
				return user;
			}
		}
		return null;
	}

	@Test(dependsOnMethods = "addUser", groups = "user")
	public void getUsers() throws ApiException {
		UserAccount user = getUserByEmail(TEST_USER_EMAIL);
		testUser = user;
		assertNotNull(user, "Test user not found");
		assertEquals(user.getEmail(), TEST_USER_EMAIL);
		assertEquals(user.getFullName(), TEST_USER_NAME);
	}

	@Test(dependsOnMethods = "getUsers", groups = "user")
	public void getUser() throws ApiException {
		Integer testUserId = testUser.getUserId();
		UserAccountRolesResults userRoles = userApi
				.getUser(testUserId);
		UserAccount gotUser = userRoles.getUser();
		assertNotNull(gotUser, "Test user not found");
		assertEquals(gotUser.getUserId(), testUserId);
		assertEquals(gotUser.getEmail(), TEST_USER_EMAIL);
		assertEquals(gotUser.getFullName(), TEST_USER_NAME);
		assertEquals(gotUser.getAccountId(), testUser.getAccountId());
	}

	@Test(dependsOnMethods = "getUsers", groups = "user")
	public void updateUser() throws ApiException {
		userApi.updateUser(testUser);

		UserAccountRolesResults updatedUserRoles = userApi
				.getUser(testUser.getUserId());
		assertNotNull(updatedUserRoles.getUser().getUpdated());
	}

	@AfterGroups(groups = "collaborator", alwaysRun = true)
	public void cleanupTestCollaborator() throws ApiException {
		if (testCollaborator != null) {
			collaboratorApi.deleteCollaborator(testCollaborator.getUserId());
		}
	}

	public void cleanupOldTestCollaborators() throws ApiException {
		for (UserAccount user : collaboratorApi.getCollaborators()) {
			if (user.getEmail().equals(TEST_COLLABORATOR_EMAIL)) {
				collaboratorApi.deleteCollaborator(user.getUserId());
			}
		}
	}

	@Test(groups = "collaborator")
	public void addCollaborator() throws ApiException {
		cleanupTestCollaborator();

		CollaboratorAddition collaboratorAddition = new CollaboratorAddition();
		collaboratorAddition.setEmail(TEST_COLLABORATOR_EMAIL);
		testCollaboratorRole = getRoleByName(TEST_COLLABORATOR_ROLE_NAME);
		collaboratorAddition.setRoleId(testCollaboratorRole.getRoleId());

		try {
			collaboratorApi.addCollaborator(collaboratorAddition);
		} catch (ApiException e) {
			if (e.getResponseBody().indexOf("already") >= 0) {
				cleanupOldTestCollaborators();
				collaboratorApi.addCollaborator(collaboratorAddition);
			} else {
				throw e;
			}
		}
	}

	@Test(dependsOnMethods = "addCollaborator", groups = "collaborator")
	public void addCollaboratorDuplicate() {
		CollaboratorAddition collaboratorAddition = new CollaboratorAddition();
		collaboratorAddition.setEmail(TEST_COLLABORATOR_EMAIL);
		collaboratorAddition.setRoleId(testCollaboratorRole.getRoleId());
		try {
			collaboratorApi.addCollaborator(collaboratorAddition);
			fail("Duplicate collaborator added?");
		} catch (ApiException e) {
			assertTrue(e.getResponseBody().indexOf("already") >= 0);
		}
	}

	protected UserAccount getCollaboratorByEmail(String collaboratorEmail)
			throws ApiException {
		List<UserAccount> collaborators = collaboratorApi.getCollaborators();
		for (UserAccount collaborator : collaborators) {
			if (collaborator.getEmail().equals(collaboratorEmail)) {
				return collaborator;
			}
		}
		return null;
	}

	@Test(dependsOnMethods = "addCollaborator", groups = "collaborator")
	public void getCollaborators() throws ApiException {
		testCollaborator = getCollaboratorByEmail(TEST_COLLABORATOR_EMAIL);
		assertNotNull(testCollaborator, "Test collaborator not found");
	}

	@Test(dependsOnMethods = "getCollaborators", groups = "collaborator")
	public void getCollaborator() throws ApiException {
		UserAccountRolesResults gotCollaborator = collaboratorApi
				.getCollaborator(testCollaborator.getUserId());
		assertNotNull(gotCollaborator, "Test collaborator not found");
		assertEquals(gotCollaborator.getUser().getUserId(),
				testCollaborator.getUserId());
	}

	@Test(dependsOnMethods = "getCollaborators", groups = "collaborator")
	public void updateCollaborator() throws ApiException {
		CollaboratorUpdate collabUpdate = new CollaboratorUpdate();
		collabUpdate.setUserId(testCollaborator.getUserId());
		collabUpdate.setRoleId(testCollaborator.getRoleId());
		collaboratorApi.updateCollaborator(collabUpdate);
	}

	@AfterGroups(groups = "environment", alwaysRun = true)
	public void cleanupTestEnvironment() throws ApiException {
		if (testEnvironment != null) {
			environmentApi.deleteEnvironment(
					testEnvironment.getDeploymentEnvironmentId());
			testEnvironment = null;
		}
	}

	public void cleanupOldTestEnvironments() throws ApiException {
		for (DeploymentEnvironment environment : environmentApi
				.getEnvironments()) {
			if (environment.getName().equals(TEST_ENVIRONMENT_NAME)) {
				environmentApi.deleteEnvironment(
						environment.getDeploymentEnvironmentId());
			}
		}
	}

	@Test(groups = "environment")
	public void addEnvironment() throws ApiException {
		// Environment names do not have to be unique.
		// This poses a test issue since startDeployment uses the environment
		// name and we want to be sure it points to the test environment created
		// here. Remove an old environments with the same name to ensure this.
		cleanupOldTestEnvironments();

		// Use a Webhook to http://example.com since Webhooks always succeed
		DeploymentEnvironmentSettings settings = new DeploymentEnvironmentSettings();
		settings.addProviderSettingsItem(new StoredNameValue().name("url")
				.value(new StoredValue().value("http://example.com")));
		DeploymentEnvironmentAddition environmentAddition = new DeploymentEnvironmentAddition()
				.name(TEST_ENVIRONMENT_NAME)
				.provider(DeploymentProviderType.WEBHOOK).settings(settings);
		DeploymentEnvironmentWithSettings environment = environmentApi.addEnvironment(environmentAddition);
		testEnvironment = environment;
		assertEquals(environment.getName(), TEST_ENVIRONMENT_NAME);
	}

	protected DeploymentEnvironment getEnvironmentByName(String name)
			throws ApiException {
		for (DeploymentEnvironment environment : environmentApi
				.getEnvironments()) {
			if (environment.getName().equals(name)) {
				return environment;
			}
		}
		return null;
	}

	@Test(dependsOnMethods = "addEnvironment", groups = "environment")
	public void getEnvironments() throws ApiException {
		DeploymentEnvironmentWithSettings environment = testEnvironment;
		DeploymentEnvironment namedEnvironment = getEnvironmentByName(
				environment.getName());
		assertEquals(namedEnvironment.getDeploymentEnvironmentId(),
				environment.getDeploymentEnvironmentId());
		assertEquals(namedEnvironment.getAccountId(),
				environment.getAccountId());
		assertEquals(namedEnvironment.getName(), environment.getName());
	}

	@Test(dependsOnMethods = "addEnvironment", groups = "environment")
	public void getEnvironmentSettings() throws ApiException {
		Integer testEnvId = testEnvironment.getDeploymentEnvironmentId();
		DeploymentEnvironmentSettingsResults gotEnvSettingsObj = environmentApi
				.getEnvironmentSettings(testEnvId);
		DeploymentEnvironmentWithSettings gotEnv = gotEnvSettingsObj
				.getEnvironment();
		assertEquals(gotEnv.getDeploymentEnvironmentId(), testEnvId);
	}

	@Test(dependsOnMethods = "addEnvironment", groups = "environment")
	public void updateEnvironment() throws ApiException {
		DeploymentEnvironmentWithSettings updatedEnv = environmentApi
				.updateEnvironment(testEnvironment);
		assertNotNull(updatedEnv);
		assertEquals(updatedEnv.getDeploymentEnvironmentId(),
				testEnvironment.getDeploymentEnvironmentId());
		assertNotNull(updatedEnv.getUpdated());
	}

	@AfterGroups(groups = "project", alwaysRun = true)
	public void cleanupTestProject() throws ApiException {
		if (testProject != null) {
			projectApi.deleteProject(testProject.getAccountName(),
					testProject.getSlug());
			testProject = null;
		}
	}

	public void cleanupOldTestProjects() throws ApiException {
		for (Project project : projectApi.getProjects()) {
			if (project.getRepositoryName().equals(TEST_PROJECT_REPO_NAME)) {
				projectApi.deleteProject(project.getAccountName(),
						project.getSlug());
			}
		}
	}

	@Test(groups = "project")
	public void addProject() throws ApiException {
		ProjectAddition projectAddition = new ProjectAddition();
		projectAddition.setRepositoryProvider(TEST_PROJECT_REPO_PROVIDER);
		projectAddition.setRepositoryName(TEST_PROJECT_REPO_NAME);
		Project project = projectApi.addProject(projectAddition);
		testProject = project;

		assertEquals(project.getRepositoryType(),
				TEST_PROJECT_REPO_PROVIDER);
		assertEquals(project.getRepositoryName(), TEST_PROJECT_REPO_NAME);
	}

	protected Project getProjectByRepositoryName(String repoName)
			throws ApiException {
		for (Project project : projectApi.getProjects()) {
			if (project.getRepositoryName().equals(TEST_PROJECT_REPO_NAME)) {
				return project;
			}
		}
		return null;
	}

	@Test(dependsOnMethods = "addProject", groups = "project")
	public void getProjects() throws ApiException {
		Project namedProject = getProjectByRepositoryName(
				TEST_PROJECT_REPO_NAME);
		assertNotNull(namedProject);
		assertEquals(namedProject.getRepositoryName(), TEST_PROJECT_REPO_NAME);
	}

	@Test(dependsOnMethods = "addProject", groups = "project")
	public void getProjectSettings() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectSettingsResults settings = projectApi
				.getProjectSettings(accountName, slug);
		ProjectWithConfiguration projectConfig = settings.getSettings();
		testProjectConfig = projectConfig;
		Project project = testProject;
		assertEquals(projectConfig.getProjectId(),
				project.getProjectId());
		assertEquals(projectConfig.getAccountId(),
				project.getAccountId());
		assertEquals(projectConfig.getAccountName(), accountName);
		assertEquals(projectConfig.getSlug(), slug);

		Project gotProject = settings.getProject();
		assertEquals(gotProject.getProjectId(), testProject.getProjectId());
		assertEquals(gotProject.getAccountId(), testProject.getAccountId());
		assertEquals(gotProject.getAccountName(), accountName);
		assertEquals(gotProject.getSlug(), slug);
	}

	@Test(dependsOnMethods = "addProject", groups = "project")
	public void getProjectSettingsYaml() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		testProjectYaml = projectApi.getProjectSettingsYaml(accountName, slug);
	}

	@Test(dependsOnMethods = "getProjectSettings", groups = "project")
	public void updateProject() throws ApiException {
		// Set dummy build/test scripts so build succeeds
		// Note: appveyor.yml is ignored outside of GitHub unless configured:
		// https://github.com/appveyor/ci/issues/1089#issuecomment-264549196
		ProjectWithConfiguration projectConfig = testProjectConfig;
		ProjectConfiguration config = projectConfig.getConfiguration();
		config.setBuildMode(BuildMode.SCRIPT);
		config.setBuildScripts(
				Arrays.asList(new Script().language(ScriptLanguage.CMD)
						.script(TEST_PROJECT_BUILD_SCRIPT)));
		config.setTestMode(TestMode.SCRIPT);
		config.setTestScripts(
				Arrays.asList(new Script().language(ScriptLanguage.CMD)
						.script(TEST_PROJECT_TEST_SCRIPT)));
		projectApi.updateProject(projectConfig);

		String accountName = projectConfig.getAccountName();
		String slug = projectConfig.getSlug();
		ProjectSettingsResults updatedProjectSettings = projectApi
				.getProjectSettings(accountName, slug);
		ProjectWithConfiguration updatedProject = updatedProjectSettings
				.getSettings();
		assertNotNull(updatedProject.getUpdated());
		ProjectConfiguration updatedConfig = updatedProject.getConfiguration();
		List<Script> buildScripts = updatedConfig.getBuildScripts();
		assertEquals(buildScripts.size(), 1);
		assertEquals(buildScripts.get(0).getScript(),
				TEST_PROJECT_BUILD_SCRIPT);
		List<Script> testScripts = updatedConfig.getTestScripts();
		assertEquals(testScripts.size(), 1);
		assertEquals(testScripts.get(0).getScript(), TEST_PROJECT_TEST_SCRIPT);
	}

	@Test(dependsOnMethods = "getProjectSettingsYaml", groups = "project")
	public void updateProjectSettingsYaml() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		projectApi.updateProjectSettingsYaml(accountName, slug,
				testProjectYaml.getBytes());
	}

	// Canceled builds do not show up in all queries, so it does not make a
	// good testBuild for the other tests. So it is run first separately.
	// Uncomment after https://github.com/cbeust/testng/pull/1158
	@Test(dependsOnMethods = "addProject", groups = "project" /*, priority = -2*/)
	public void cancelBuild() throws ApiException {
		// Create a new build to cancel, since cancelled builds do not show
		// up in all queries, it would not be good for testBuild to be cancelled
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		BuildStartRequest buildStart = new BuildStartRequest()
				.accountName(accountName).projectSlug(slug);
		Build cancelBuild = buildApi.startBuild(buildStart);
		String version = cancelBuild.getVersion();
		buildApi.cancelBuild(accountName, slug, version);
		ProjectBuildResults cancelledBuildProject = projectApi
				.getProjectBuildByVersion(accountName, slug, version);
		Build cancelledBuild = cancelledBuildProject.getBuild();
		Status cancelledBuildStatus = cancelledBuild.getStatus();
		assertTrue(cancelledBuildStatus == Status.CANCELLED
				|| cancelledBuildStatus == Status.CANCELLING);
	}

	// Make sure this is run after the cancelled build is done so the test
	// build gets assigned TEST_PROJECT_BUILD_NUMBER set here.
	// Note: cancelBuild can be removed from dependsOnMethods once TestNG with
	// https://github.com/cbeust/testng/pull/1158 is released
	@Test(dependsOnMethods = { "cancelBuild",
			"updateProject" }, groups = "project")
	public void updateProjectBuildNumber() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectBuildNumberUpdate pbnu = new ProjectBuildNumberUpdate()
				.nextBuildNumber(TEST_PROJECT_BUILD_NUMBER);
		projectApi.updateProjectBuildNumber(accountName, slug, pbnu);
	}

	// Set priority < 0 so build is started early and can run in background
	// Uncomment after https://github.com/cbeust/testng/pull/1158
	@Test(dependsOnMethods = "updateProjectBuildNumber", groups = "project" /*, priority = -1*/)
	public void startBuild() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		BuildStartRequest buildStart = new BuildStartRequest()
				.accountName(accountName).projectSlug(slug)
				.branch(TEST_PROJECT_BRANCH)
				.putEnvironmentVariablesItem("TEST_VAR", "1");
		Build build = buildApi.startBuild(buildStart);
		testBuild = build;
		assertEquals(build.getBranch(), TEST_PROJECT_BRANCH);
		assertEquals(build.getBuildNumber(), TEST_PROJECT_BUILD_NUMBER);
	}

	// This is not really a test, but is used for synchronization by other tests
	// Set priority > 0 so build can run during default priority tests
	// Uncomment after https://github.com/cbeust/testng/pull/1158
	@Test(dependsOnMethods = "startBuild", groups = "project" /*, priority = 1 */)
	public void waitForBuild() throws ApiException, InterruptedException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		String version = testBuild.getVersion();
		ProjectBuildResults projectBuild;
		Status buildStatus;
		while (true) {
			projectBuild = projectApi.getProjectBuildByVersion(accountName,
					slug, version);
			buildStatus = projectBuild.getBuild().getStatus();
			if (buildStatus != Status.QUEUED && buildStatus != Status.RUNNING) {
				break;
			}
			Thread.sleep(1000);
		}

		Build build = projectBuild.getBuild();
		testBuild = build;
		assertNotNull(build.getFinished());
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getBuildLog()
			throws ApiException, FileNotFoundException, IOException {
		File buildLog = buildApi
				.getBuildLog(testBuild.getJobs().get(0).getJobId());
		assertTrue(buildLog.exists());
		try (BufferedReader buildLogReader = new BufferedReader(
				new FileReader(buildLog))) {
			assertTrue(buildLogReader.readLine().contains("Build started"));
		} finally {
			buildLog.delete();
		}
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void successfulBuild() {
		assertEquals(testBuild.getStatus(), Status.SUCCESS);
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getProjectLastBuild() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectBuildResults lastProjectBuild = projectApi
				.getProjectLastBuild(accountName, slug);
		Project project = lastProjectBuild.getProject();
		assertEquals(project.getAccountName(), accountName);
		assertEquals(project.getSlug(), slug);
		Build lastBuild = lastProjectBuild.getBuild();
		assertEquals(lastBuild.getBuildId(), testBuild.getBuildId());
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getProjectLastBuildBranch() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		String branch = testBuild.getBranch();
		ProjectBuildResults branchBuild = projectApi
				.getProjectLastBuildBranch(accountName, slug, branch);
		Project project = branchBuild.getProject();
		assertEquals(project.getAccountName(), accountName);
		assertEquals(project.getSlug(), slug);
		Build build = branchBuild.getBuild();
		assertEquals(build.getBuildId(), testBuild.getBuildId());
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getProjectHistory() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectHistory history = projectApi.getProjectHistory(accountName, slug,
				10, null, null);

		Project project = history.getProject();
		assertEquals(project.getAccountName(), accountName);
		assertEquals(project.getSlug(), slug);

		List<Build> builds = history.getBuilds();
		assertNotEquals(builds.size(), 0);
		Build lastBuild = builds.get(0);
		assertEquals(lastBuild.getBuildId(), testBuild.getBuildId());
	}

	@Test(dependsOnMethods = { "addEnvironment", "addProject",
			"successfulBuild" }, groups = { "environment", "project" })
	public void startDeployment() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		String buildJobId = testBuild.getJobs().get(0).getJobId();
		DeploymentStartRequest deploymentStart = new DeploymentStartRequest();
		deploymentStart.setEnvironmentName(testEnvironment.getName());
		deploymentStart.setAccountName(accountName);
		deploymentStart.setProjectSlug(slug);
		deploymentStart.setBuildVersion(testBuild.getVersion());
		deploymentStart.setBuildJobId(buildJobId);
		testDeployment = deploymentApi.startDeployment(deploymentStart);
		Build build = testDeployment.getBuild();
		assertEquals(build.getBuildId(), testBuild.getBuildId());
		assertEquals(build.getVersion(), testBuild.getVersion());
		DeploymentEnvironment environment = testDeployment.getEnvironment();
		assertEquals(environment.getDeploymentEnvironmentId(),
				testEnvironment.getDeploymentEnvironmentId());
	}

	// This is not really a test, but is used for synchronization by other tests
	// Set priority > 0 so build can run during default priority tests
	@Test(dependsOnMethods = "startDeployment", groups = { "environment",
			"project" })
	public void waitForDeployment() throws ApiException, InterruptedException {
		Integer deploymentId = testDeployment.getDeploymentId();
		Deployment deployment;
		Status deploymentStatus;
		while (true) {
			ProjectDeployment projectDeployment = deploymentApi
					.getDeployment(deploymentId);
			deployment = projectDeployment.getDeployment();
			deploymentStatus = deployment.getStatus();
			if (deploymentStatus != Status.QUEUED
					&& deploymentStatus != Status.RUNNING) {
				break;
			}
			Thread.sleep(1000);
		}

		testDeployment = deployment;
		// Note: This may fail if AppVeyor starts checking Webhook results
		// In that case, adjust Webhook URL to something valid.
		assertEquals(deploymentStatus, Status.SUCCESS);
		assertNotNull(deployment.getFinished());
	}

	@Test(dependsOnMethods = "waitForDeployment", groups = { "environment",
			"project" })
	public void getEnvironmentDeployments() throws ApiException {
		Integer testEnvId = testEnvironment.getDeploymentEnvironmentId();
		DeploymentEnvironmentDeploymentsResults envDeps = environmentApi
				.getEnvironmentDeployments(testEnvId);
		assertEquals(envDeps.getEnvironment().getDeploymentEnvironmentId(),
				testEnvId);
		ProjectDeployment projectDeployment = envDeps.getDeployments().get(0);
		Project project = projectDeployment.getProject();
		assertEquals(project.getProjectId(), testProject.getProjectId());
		DeploymentEnvironment environment = envDeps.getEnvironment();
		assertEquals(environment.getDeploymentEnvironmentId(), testEnvId);
		Deployment deployment = projectDeployment.getDeployment();
		assertEquals(deployment.getDeploymentId(),
				testDeployment.getDeploymentId());
	}

	@Test(dependsOnMethods = "waitForDeployment", groups = { "environment",
			"project" })
	public void getProjectDeployments() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectDeploymentsResults projectDeployments = projectApi
				.getProjectDeployments(accountName, slug);
		Project project = projectDeployments.getProject();
		assertEquals(project.getAccountName(), accountName);
		assertEquals(project.getSlug(), slug);
		List<DeploymentEnvironmentDeployment> environmentDeployments = projectDeployments
				.getDeployments();
		DeploymentEnvironmentDeployment environmentDeployment = environmentDeployments
				.get(0);
		DeploymentEnvironment environment = environmentDeployment
				.getEnvironment();
		assertEquals(environment.getDeploymentEnvironmentId(),
				testEnvironment.getDeploymentEnvironmentId());
		Deployment deployment = environmentDeployment.getDeployment();
		assertEquals(deployment.getDeploymentId(),
				testDeployment.getDeploymentId());
	}

	public static void main(String[] args) throws ApiException {
		ApiTest apiTest = new ApiTest();
		apiTest.cleanupOldTestProjects();
		apiTest.cleanupOldTestEnvironments();
		apiTest.cleanupOldTestCollaborators();
		apiTest.cleanupOldTestUsers();
		apiTest.cleanupOldTestRoles();
	}
}