package name.kevinlocke.appveyor;

import static name.kevinlocke.appveyor.testutils.AssertMediaType.assertIsPng;
import static name.kevinlocke.appveyor.testutils.AssertMediaType.assertIsSvg;
import static name.kevinlocke.appveyor.testutils.AssertModels.assertModelAgrees;
import static name.kevinlocke.appveyor.testutils.AssertModels.assertModelAgreesExcluding;
import static name.kevinlocke.appveyor.testutils.AssertModels.assertModelEquals;
import static name.kevinlocke.appveyor.testutils.AssertModels.assertModelEqualsExcluding;
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
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.Test;

import com.migcomponents.migbase64.Base64;

import name.kevinlocke.appveyor.api.BuildApi;
import name.kevinlocke.appveyor.api.CollaboratorApi;
import name.kevinlocke.appveyor.api.DeploymentApi;
import name.kevinlocke.appveyor.api.EnvironmentApi;
import name.kevinlocke.appveyor.api.ProjectApi;
import name.kevinlocke.appveyor.api.RoleApi;
import name.kevinlocke.appveyor.api.UserApi;
import name.kevinlocke.appveyor.model.ArtifactModel;
import name.kevinlocke.appveyor.model.Build;
import name.kevinlocke.appveyor.model.BuildMode;
import name.kevinlocke.appveyor.model.BuildStartRequest;
import name.kevinlocke.appveyor.model.CollaboratorAddition;
import name.kevinlocke.appveyor.model.CollaboratorUpdate;
import name.kevinlocke.appveyor.model.Deployment;
import name.kevinlocke.appveyor.model.DeploymentEnvironment;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentAddition;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentDeploymentsResults;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentLookupModel;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentProject;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentSettings;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentSettingsResults;
import name.kevinlocke.appveyor.model.DeploymentEnvironmentWithSettings;
import name.kevinlocke.appveyor.model.DeploymentProviderType;
import name.kevinlocke.appveyor.model.DeploymentStartRequest;
import name.kevinlocke.appveyor.model.EncryptRequest;
import name.kevinlocke.appveyor.model.EnvironmentDeploymentModel;
import name.kevinlocke.appveyor.model.NuGetFeed;
import name.kevinlocke.appveyor.model.Project;
import name.kevinlocke.appveyor.model.ProjectAddition;
import name.kevinlocke.appveyor.model.ProjectBuildNumberUpdate;
import name.kevinlocke.appveyor.model.ProjectBuildResults;
import name.kevinlocke.appveyor.model.ProjectConfiguration;
import name.kevinlocke.appveyor.model.ProjectDeployment;
import name.kevinlocke.appveyor.model.ProjectDeploymentModel;
import name.kevinlocke.appveyor.model.ProjectDeploymentsResults;
import name.kevinlocke.appveyor.model.ProjectHistory;
import name.kevinlocke.appveyor.model.ProjectSettingsResults;
import name.kevinlocke.appveyor.model.ProjectWithConfiguration;
import name.kevinlocke.appveyor.model.ReRunBuildRequest;
import name.kevinlocke.appveyor.model.RepositoryProvider;
import name.kevinlocke.appveyor.model.Role;
import name.kevinlocke.appveyor.model.RoleAce;
import name.kevinlocke.appveyor.model.RoleAddition;
import name.kevinlocke.appveyor.model.RoleWithGroups;
import name.kevinlocke.appveyor.model.Script;
import name.kevinlocke.appveyor.model.ScriptLanguage;
import name.kevinlocke.appveyor.model.SecurityDescriptor;
import name.kevinlocke.appveyor.model.Status;
import name.kevinlocke.appveyor.model.StoredNameValue;
import name.kevinlocke.appveyor.model.StoredValue;
import name.kevinlocke.appveyor.model.TestMode;
import name.kevinlocke.appveyor.model.UserAccount;
import name.kevinlocke.appveyor.model.UserAccountRolesResults;
import name.kevinlocke.appveyor.model.UserAddition;
import name.kevinlocke.appveyor.testutils.Resources;
import name.kevinlocke.appveyor.testutils.TestApiClient;
import name.kevinlocke.appveyor.testutils.json.FieldNameExclusionStrategy;

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
	/**
	 * Gets a short random string safe for use in URLs and filenames.
	 *
	 * Names for resources created by tests are made unique to avoid collisions
	 * during concurrent test runs. This is particularly a problem for
	 * deployment environments where names are not unique and startDeployment
	 * identifies the environment by name resulting in an unavoidable race.
	 */
	protected static final String randStr() {
		byte[] randBytes = new byte[6];
		ThreadLocalRandom.current().nextBytes(randBytes);
		String randStr = Base64.encodeToString(randBytes, false);
		String randUrlSafe = randStr.replace('+', '-').replace('/', '_');
		return randUrlSafe;
	}

	public static final String TEST_BADGE_PROVIDER = RepositoryProvider.GITHUB
			.toString();
	public static final String TEST_BADGE_ACCOUNT = "gruntjs";
	public static final String TEST_BADGE_SLUG = "grunt";
	// AppVeyor account must exist and be different than APPVEYOR_API_TOKEN acct
	public static final String TEST_COLLABORATOR_EMAIL = "appveyor-swagger@example.com";
	public static final String TEST_COLLABORATOR_ROLE_NAME = "User";
	public static final String TEST_ENCRYPT_VALUE = "encryptme";
	public static final String TEST_ENVIRONMENT_PREFIX = "Test Env ";
	public static final String TEST_ENVIRONMENT_NAME = TEST_ENVIRONMENT_PREFIX
			+ randStr();
	public static final Integer TEST_PROJECT_BUILD_NUMBER = 45;
	public static final String TEST_PROJECT_BUILD_SCRIPT = Resources
			.getAsString("/buildscript.ps1");
	public static final String TEST_PROJECT_BRANCH = "master";
	// Note: Using GitHub provider requires GitHub auth to AppVeyor test user
	public static final RepositoryProvider TEST_PROJECT_REPO_PROVIDER = RepositoryProvider.GIT;
	public static final String TEST_PROJECT_REPO_NAME = "https://github.com/kevinoid/empty.git";
	public static final String TEST_PROJECT_TEST_SCRIPT = Resources
			.getAsString("/testscript.ps1");
	public static final String TEST_ROLE_PREFIX = "Test Role ";
	public static final String TEST_ROLE_NAME = TEST_ROLE_PREFIX + randStr();
	public static final String TEST_USER_EMAIL_AT_DOMAIN = "@example.com";
	public static final String TEST_USER_EMAIL = randStr()
			+ TEST_USER_EMAIL_AT_DOMAIN;
	public static final String TEST_USER_PREFIX = "Test User ";
	public static final String TEST_USER_NAME = TEST_USER_PREFIX + randStr();
	public static final String TEST_USER_ROLE_NAME = "User";

	// Exclude jobs property of a build for operations which do not return it
	private static final FieldNameExclusionStrategy buildExcludeJobs = new FieldNameExclusionStrategy(
			"jobs", "updated");
	// The message counts are updated asychronously via the Build Worker API.
	// Messages can be added after the build reaches a final state and are
	// therefore not reliable enough for comparison in the tests.
	private static final FieldNameExclusionStrategy buildJobExcludes = new FieldNameExclusionStrategy(
			"compilationErrorsCount", "compilationMessagesCount",
			"compilationWarningsCount", "failedTestsCount", "messagesCount",
			"passedTestsCount", "testsCount", "updated");
	// Exclude updated field due to change on update operation
	private static final FieldNameExclusionStrategy excludeUpdated = new FieldNameExclusionStrategy(
			"updated");
	// Exclude currentBuildId, nuGetFeed, repositoryBranch, securityDescriptor when comparing
	// Project results from endpoints which don't include these
	// Exclude builds and updated which change as the result of other tests
	private static final FieldNameExclusionStrategy projectExcludes = new FieldNameExclusionStrategy(
			"builds", "currentBuildId", "nuGetFeed", "repositoryBranch", "securityDescriptor",
			"updated");

	protected final ApiClient apiClient;
	protected final BuildApi buildApi;
	protected final CollaboratorApi collaboratorApi;
	protected final DeploymentApi deploymentApi;
	protected final EnvironmentApi environmentApi;
	protected final ProjectApi projectApi;
	protected final RoleApi roleApi;
	protected final UserApi userApi;

	private final ReentrantLock systemRolesLock = new ReentrantLock();
	private volatile Map<String, Role> systemRolesByName;

	protected volatile ArtifactModel testArtifact;
	protected volatile ArtifactModel testArtifactPath;
	protected volatile Build testBuild;
	protected volatile UserAccount testCollaborator;
	protected volatile Deployment testDeployment;
	protected volatile DeploymentEnvironmentWithSettings testEnvironment;
	protected volatile Project testProject;
	protected volatile ProjectWithConfiguration testProjectConfig;
	protected volatile String testProjectYaml;
	protected volatile Build testRebuild;
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

	/**
	 * "Normalizes" a SecurityDescriptor to only include system roles, to make
	 * it consistent regardless of which roles exist.
	 */
	protected void normalizeSecurity(SecurityDescriptor securityDescriptor)
			throws ApiException {
		Iterator<RoleAce> roleAcesIter = securityDescriptor.getRoleAces()
				.iterator();
		while (roleAcesIter.hasNext()) {
			RoleAce roleAce = roleAcesIter.next();
			if (getSystemRoleByName(roleAce.getName()) == null) {
				roleAcesIter.remove();
			}
		}
	}

	/**
	 * "Normalizes" the SecurityDescriptor to only include system roles, to make
	 * it consistent regardless of which roles exist.
	 */
	protected void normalizeSecurity(
			DeploymentEnvironment deploymentEnvironment) throws ApiException {
		normalizeSecurity(deploymentEnvironment.getSecurityDescriptor());
	}

	/**
	 * "Normalizes" the SecurityDescriptor to only include system roles, to make
	 * it consistent regardless of which roles exist.
	 */
	protected void normalizeSecurity(
			DeploymentEnvironmentWithSettings deploymentEnvironment)
			throws ApiException {
		normalizeSecurity(deploymentEnvironment.getSecurityDescriptor());
	}

	/**
	 * "Normalizes" the SecurityDescriptor to only include system roles, to make
	 * it consistent regardless of which roles exist.
	 */
	protected void normalizeSecurity(Project project) throws ApiException {
		normalizeSecurity(project.getSecurityDescriptor());
	}

	/**
	 * "Normalizes" the SecurityDescriptor to only include system roles, to make
	 * it consistent regardless of which roles exist.
	 */
	protected void normalizeSecurity(ProjectWithConfiguration project)
			throws ApiException {
		normalizeSecurity(project.getSecurityDescriptor());
	}

	@AfterGroups(groups = "role", alwaysRun = true)
	public void cleanupTestRole() throws ApiException {
		if (testRole != null) {
			roleApi.deleteRole(testRole.getRoleId());
			testRole = null;
		}
	}

	public void cleanupOldTestRoles() throws ApiException {
		for (Role role : getRolesInternal()) {
			if (role.getName().startsWith(TEST_ROLE_PREFIX)) {
				roleApi.deleteRole(role.getRoleId());
			}
		}
	}

	@Test(groups = "role")
	public void addRole() throws ApiException {
		RoleAddition roleAddition = new RoleAddition().name(TEST_ROLE_NAME);
		RoleWithGroups role = roleApi.addRole(roleAddition);
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
		assertModelEqualsExcluding(gotRole, testRole, excludeUpdated);
	}

	/** Gets Roles and caches (immutable) system roles if not yet cached. */
	protected List<Role> getRolesInternal() throws ApiException {
		// If the system roles haven't been cached yet, do the caching.
		//
		// Note: The lock must be held before the API call so that calls to
		// getSystemRoleByName while roleApi.getRoles() is in progress do not
		// cause a second (unnecessary) API call.
		if (systemRolesByName == null) {
			// Note: tryLock() so non-system calls do not block unnecessarily
			if (systemRolesLock.tryLock()) {
				try {
					if (systemRolesByName == null) {
						List<Role> roles = roleApi.getRoles();
						Map<String, Role> newSystemRolesByName = new TreeMap<>();
						for (Role role : roles) {
							if (role.getIsSystem()) {
								newSystemRolesByName.put(role.getName(), role);
							}
						}
						systemRolesByName = newSystemRolesByName;
						return roles;
					}
				} finally {
					systemRolesLock.unlock();
				}
			}
		}

		return roleApi.getRoles();
	}

	protected Role getRoleByName(String roleName) throws ApiException {
		for (Role role : getRolesInternal()) {
			if (role.getName().equals(roleName)) {
				return role;
			}
		}
		return null;
	}

	protected Role getSystemRoleByName(String roleName) throws ApiException {
		if (systemRolesByName == null) {
			systemRolesLock.lock();
			try {
				if (systemRolesByName == null) {
					getRolesInternal();
				}
			} finally {
				systemRolesLock.unlock();
			}
		}

		return systemRolesByName.get(roleName);
	}

	protected Collection<Role> getSystemRoles() throws ApiException {
		if (systemRolesByName == null) {
			systemRolesLock.lock();
			try {
				if (systemRolesByName == null) {
					getRolesInternal();
				}
			} finally {
				systemRolesLock.unlock();
			}
		}

		return systemRolesByName.values();
	}

	protected void assertHasSystemRoles(Iterable<Role> roles)
			throws ApiException {
		TreeSet<Role> systemRoles = new TreeSet<>(new RoleNameComparator());
		for (Role role : roles) {
			if (role.getIsSystem()) {
				systemRoles.add(role);
			}
		}
		assertModelEquals(systemRoles, getSystemRoles());
	}

	@Test(dependsOnMethods = "addRole", groups = "role")
	public void getRoles() throws ApiException {
		Role testRoleByName = getRoleByName(TEST_ROLE_NAME);
		assertNotNull(testRoleByName, TEST_ROLE_NAME + " not in list!?");
		assertModelAgreesExcluding(testRoleByName, testRole, excludeUpdated);
	}

	@Test(dependsOnMethods = "addRole", groups = "role")
	public void updateRole() throws ApiException {
		RoleWithGroups updatedRole = roleApi.updateRole(testRole);
		assertNotNull(updatedRole);
		assertNotNull(updatedRole.getUpdated());
		assertModelAgreesExcluding(updatedRole, testRole, excludeUpdated);
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
			if (user.getEmail().endsWith(TEST_USER_EMAIL_AT_DOMAIN)) {
				userApi.deleteUser(user.getUserId());
			}
		}
	}

	@Test(groups = "user")
	public void addUser() throws ApiException {
		UserAddition userAddition = new UserAddition();
		userAddition.setFullName(TEST_USER_NAME);
		userAddition.setEmail(TEST_USER_EMAIL);
		Role role = getSystemRoleByName(TEST_USER_ROLE_NAME);
		userAddition.setRoleId(role.getRoleId());
		userApi.addUser(userAddition);
	}

	@Test(dependsOnMethods = "addUser", groups = "user")
	public void addUserDuplicate() throws ApiException {
		UserAddition userAddition = new UserAddition();
		userAddition.setFullName(TEST_USER_NAME);
		userAddition.setEmail(TEST_USER_EMAIL);
		Role role = getSystemRoleByName(TEST_USER_ROLE_NAME);
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
		UserAccountRolesResults userRoles = userApi.getUser(testUserId);
		UserAccount gotUser = userRoles.getUser();
		assertNotNull(gotUser, "Test user not found");
		assertModelEqualsExcluding(gotUser, testUser, excludeUpdated);
		assertHasSystemRoles(userRoles.getRoles());
	}

	@Test(dependsOnMethods = "getUsers", groups = "user")
	public void updateUser() throws ApiException {
		userApi.updateUser(testUser);

		UserAccountRolesResults updatedUserWithRoles = userApi
				.getUser(testUser.getUserId());
		UserAccount updatedUser = updatedUserWithRoles.getUser();
		assertNotNull(updatedUser.getUpdated());
		assertModelEqualsExcluding(updatedUser, testUser, excludeUpdated);
		assertHasSystemRoles(updatedUserWithRoles.getRoles());
	}

	@AfterGroups(groups = "collaborator", alwaysRun = true)
	public void cleanupTestCollaborator() throws ApiException {
		if (testCollaborator != null) {
			collaboratorApi.deleteCollaborator(testCollaborator.getUserId());
			testCollaborator = null;
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
		CollaboratorAddition collaboratorAddition = new CollaboratorAddition();
		collaboratorAddition.setEmail(TEST_COLLABORATOR_EMAIL);
		int roleId = getSystemRoleByName(TEST_COLLABORATOR_ROLE_NAME)
				.getRoleId();
		collaboratorAddition.setRoleId(roleId);

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
	public void addCollaboratorDuplicate() throws ApiException {
		CollaboratorAddition collaboratorAddition = new CollaboratorAddition();
		collaboratorAddition.setEmail(TEST_COLLABORATOR_EMAIL);
		int roleId = getSystemRoleByName(TEST_COLLABORATOR_ROLE_NAME)
				.getRoleId();
		collaboratorAddition.setRoleId(roleId);
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
		UserAccount collaborator = getCollaboratorByEmail(
				TEST_COLLABORATOR_EMAIL);
		assertNotNull(collaborator, "Test collaborator not found");
		testCollaborator = collaborator;
		assertEquals(collaborator.getRoleName(), TEST_COLLABORATOR_ROLE_NAME);
		Integer roleId = getSystemRoleByName(TEST_COLLABORATOR_ROLE_NAME)
				.getRoleId();
		assertEquals(collaborator.getRoleId(), roleId);
	}

	@Test(dependsOnMethods = "getCollaborators", groups = "collaborator")
	public void getCollaborator() throws ApiException {
		UserAccountRolesResults collaboratorWithRoles = collaboratorApi
				.getCollaborator(testCollaborator.getUserId());
		assertNotNull(collaboratorWithRoles, "Test collaborator not found");

		UserAccount gotCollaborator = collaboratorWithRoles.getUser();
		assertModelEqualsExcluding(gotCollaborator, testCollaborator,
				excludeUpdated);

		assertHasSystemRoles(collaboratorWithRoles.getRoles());
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
		for (DeploymentEnvironmentLookupModel environment : environmentApi
				.getEnvironments()) {
			if (environment.getName().startsWith(TEST_ENVIRONMENT_PREFIX)) {
				environmentApi.deleteEnvironment(
						environment.getDeploymentEnvironmentId());
			}
		}
	}

	@Test(groups = "environment")
	public void addEnvironment() throws ApiException {
		// Use a Webhook to http://example.com since Webhooks always succeed
		DeploymentEnvironmentSettings settings = new DeploymentEnvironmentSettings();
		settings.addProviderSettingsItem(new StoredNameValue().name("url")
				.value(new StoredValue().value("http://example.com")));
		DeploymentEnvironmentAddition environmentAddition = new DeploymentEnvironmentAddition()
				.name(TEST_ENVIRONMENT_NAME)
				.provider(DeploymentProviderType.WEBHOOK).settings(settings);
		DeploymentEnvironmentWithSettings environment = environmentApi
				.addEnvironment(environmentAddition);
		normalizeSecurity(environment);
		testEnvironment = environment;
		assertEquals(environment.getName(), TEST_ENVIRONMENT_NAME);
	}

	protected DeploymentEnvironmentLookupModel getEnvironmentByName(String name)
			throws ApiException {
		for (DeploymentEnvironmentLookupModel environment : environmentApi
				.getEnvironments()) {
			if (environment.getName().equals(name)) {
				return environment;
			}
		}
		return null;
	}

	@Test(dependsOnMethods = "addEnvironment", groups = "environment")
	public void getEnvironments() throws ApiException {
		DeploymentEnvironmentLookupModel namedEnvironment = getEnvironmentByName(
				testEnvironment.getName());
		assertModelAgrees(namedEnvironment, testEnvironment);
	}

	@Test(dependsOnMethods = "addEnvironment", groups = "environment")
	public void getEnvironmentSettings() throws ApiException {
		Integer testEnvId = testEnvironment.getDeploymentEnvironmentId();
		DeploymentEnvironmentSettingsResults gotEnvSettingsObj = environmentApi
				.getEnvironmentSettings(testEnvId);
		DeploymentEnvironmentWithSettings gotEnv = gotEnvSettingsObj
				.getEnvironment();

		List<DeploymentEnvironmentProject> projects = gotEnv.getProjects();
		assertNotEquals(projects.size(), 0);
		DeploymentEnvironmentProject project = projects.get(0);
		// Assert that project contains expected properties
		assertNotEquals(project.getName().length(), 0);
		assertNotEquals((int) project.getProjectId(), 0);
		assertNotNull(project.getIsSelected());

		List<DeploymentEnvironmentProject> emptyProjects = Collections
				.emptyList();
		gotEnv.setProjects(emptyProjects);
		normalizeSecurity(gotEnv);
		assertModelEqualsExcluding(gotEnv, testEnvironment, excludeUpdated);
	}

	@Test(dependsOnMethods = "addEnvironment", groups = "environment")
	public void updateEnvironment() throws ApiException {
		DeploymentEnvironmentWithSettings updatedEnv = environmentApi
				.updateEnvironment(testEnvironment);
		assertNotNull(updatedEnv);
		normalizeSecurity(updatedEnv);
		assertModelEqualsExcluding(updatedEnv, testEnvironment, excludeUpdated);
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

	@Test
	public void encryptValue() throws ApiException {
		EncryptRequest encryptReq = new EncryptRequest();
		encryptReq.setPlainValue(TEST_ENCRYPT_VALUE);
		String encrypted = projectApi.encryptValue(encryptReq);
		// This isn't an API guarantee, just a sanity check
		assertTrue(Pattern.matches("^[A-Za-z0-9/+]+={0,2}$", encrypted),
				encrypted + " is base64-encoded string");
	}

	@Test(groups = "project")
	public void addProject() throws ApiException {
		ProjectAddition projectAddition = new ProjectAddition();
		projectAddition.setRepositoryProvider(TEST_PROJECT_REPO_PROVIDER);
		projectAddition.setRepositoryName(TEST_PROJECT_REPO_NAME);
		Project project = projectApi.addProject(projectAddition);
		testProject = project;

		assertEquals(project.getRepositoryType(), TEST_PROJECT_REPO_PROVIDER);
		assertEquals(project.getRepositoryName(), TEST_PROJECT_REPO_NAME);
	}

	@Test(dependsOnMethods = "addProject", groups = "project")
	public void getProjects() throws ApiException {
		int testProjectId = testProject.getProjectId();
		Project foundProject = null;
		for (Project project : projectApi.getProjects()) {
			if (project.getProjectId() == testProjectId) {
				foundProject = project;
				break;
			}
		}
		assertNotNull(foundProject);

		assertModelEqualsExcluding(foundProject, testProject, projectExcludes);
	}

	// Note: Does not depend on startBuild since it creates a separate build
	// to avoid interfering with testBuild (cancelled builds do not show
	// up in all queries).
	@Test(dependsOnMethods = "addProject", groups = "project")
	public void cancelBuild() throws ApiException {
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

	@Test(dependsOnMethods = "addProject", groups = "project")
	public void getProjectSettings() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectSettingsResults settings = projectApi
				.getProjectSettings(accountName, slug);
		ProjectWithConfiguration projectConfig = settings.getSettings();
		normalizeSecurity(projectConfig);
		testProjectConfig = projectConfig;
		Project project = settings.getProject();

		// Check both copies in response agree
		assertModelAgreesExcluding(projectConfig, project, projectExcludes);
		// Check each agrees with testProject
		assertModelAgreesExcluding(projectConfig, testProject, projectExcludes);
		assertModelEqualsExcluding(project, testProject, projectExcludes);

		assertEquals(projectConfig.getRepositoryBranch(), TEST_PROJECT_BRANCH);

		NuGetFeed nuGetFeed = projectConfig.getNuGetFeed();
		assertNotNull(nuGetFeed.getAccountId());
		assertNotNull(nuGetFeed.getCreated());
		assertNotNull(nuGetFeed.getId());
		assertNotNull(nuGetFeed.getName());
		assertNotNull(nuGetFeed.getProjectId());
		assertNotNull(nuGetFeed.getPublishingEnabled());

		SecurityDescriptor security = projectConfig.getSecurityDescriptor();
		assertNotNull(security);
		assertNotEquals(security.getAccessRightDefinitions().size(), 0);
		assertNotEquals(security.getRoleAces().size(), 0);
	}

	@Test(dependsOnMethods = "addProject", groups = "project")
	public void getProjectSettingsYaml() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		testProjectYaml = projectApi.getProjectSettingsYaml(accountName, slug);
	}

	// Run after updateProjectSettingsYaml to ensure build scripts are
	// configured for startBuild and not reset by the Yaml get/update ordering.
	@Test(dependsOnMethods = { "getProjectSettings",
			"updateProjectSettingsYaml" }, groups = "project")
	public void updateProject() throws ApiException {
		// Set dummy build/test scripts so build succeeds
		// Note: appveyor.yml is ignored outside of GitHub unless configured:
		// https://github.com/appveyor/ci/issues/1089#issuecomment-264549196
		ProjectWithConfiguration projectConfig = testProjectConfig;
		ProjectConfiguration config = projectConfig.getConfiguration();

		// Set environment variables for getProjectEnvironmentVariables
		config.addEnvironmentVariablesItem(new StoredNameValue().name("NULL")
				.value(new StoredValue().isEncrypted(false)));
		config.addEnvironmentVariablesItem(new StoredNameValue().name("EMPTY")
				.value(new StoredValue().isEncrypted(false).value("")));
		config.addEnvironmentVariablesItem(
				new StoredNameValue().name("UNTRIMMED").value(
						new StoredValue().isEncrypted(false).value(" val ")));
		config.addEnvironmentVariablesItem(new StoredNameValue().name("NUM")
				.value(new StoredValue().isEncrypted(false).value("1")));
		config.addEnvironmentVariablesItem(
				new StoredNameValue().name("ENCRYPTED").value(
						new StoredValue().isEncrypted(true).value("foo")));

		config.setBuildMode(BuildMode.SCRIPT);
		config.setBuildScripts(
				Arrays.asList(new Script().language(ScriptLanguage.PS)
						.script(TEST_PROJECT_BUILD_SCRIPT)));
		config.setTestMode(TestMode.SCRIPT);
		config.setTestScripts(Arrays.asList(new Script()
				.language(ScriptLanguage.PS).script(TEST_PROJECT_TEST_SCRIPT)));
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

	@Test(dependsOnMethods = "updateProject", groups = "project")
	public void getProjectEnvironmentVariables() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		List<StoredNameValue> envVars = projectApi
				.getProjectEnvironmentVariables(accountName, slug);

		List<StoredNameValue> testVars = testProjectConfig.getConfiguration()
				.getEnvironmentVariables();
		assertNotEquals(testVars.size(), 0);
		assertModelAgrees(envVars, testVars);
	}

	@Test(dependsOnMethods = "getProjectEnvironmentVariables", groups = "project")
	public void updateProjectEnvironmentVariables() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		List<StoredNameValue> newVars = Arrays
				.asList(new StoredNameValue().name("UPDATED").value(
						new StoredValue().isEncrypted(false).value("value")));
		projectApi.updateProjectEnvironmentVariables(accountName, slug,
				newVars);
		List<StoredNameValue> envVars = projectApi
				.getProjectEnvironmentVariables(accountName, slug);
		assertModelAgrees(envVars, newVars);
	}

	@Test(dependsOnMethods = "getProjectSettingsYaml", groups = "project")
	public void updateProjectSettingsYaml() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		projectApi.updateProjectSettingsYaml(accountName, slug,
				testProjectYaml.getBytes());
	}

	// Depends on cancelBuild so that the build number will be set after the
	// cancelled build is started. That way there is no chance of the build
	// number being used by cancelBuild instead of startBuild.
	@Test(dependsOnMethods = { "cancelBuild",
			"updateProject" }, groups = "project")
	public void updateProjectBuildNumber() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectBuildNumberUpdate pbnu = new ProjectBuildNumberUpdate()
				.nextBuildNumber(TEST_PROJECT_BUILD_NUMBER);
		projectApi.updateProjectBuildNumber(accountName, slug, pbnu);
	}

	@Test(dependsOnMethods = "updateProjectBuildNumber", groups = "project")
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

	private ProjectBuildResults waitForBuild(String accountName, String slug, String version)
			throws ApiException, InterruptedException
	{
		while (true) {
			ProjectBuildResults projectBuild = projectApi.getProjectBuildByVersion(accountName,
					slug, version);
			Status buildStatus = projectBuild.getBuild().getStatus();
			if (buildStatus != Status.QUEUED && buildStatus != Status.RUNNING) {
				return projectBuild;
			}
			Thread.sleep(1000);
		}
	}

	// This is not really a test, but is used for synchronization by other tests
	@Test(dependsOnMethods = "startBuild", groups = "project")
	public void waitForBuild() throws ApiException, InterruptedException {
		ProjectBuildResults projectBuild = this.waitForBuild(
				testProject.getAccountName(),
				testProject.getSlug(),
				testBuild.getVersion());
		Build build = projectBuild.getBuild();
		testBuild = build;
		assertNotNull(build.getFinished());
		Project project = projectBuild.getProject();
		assertModelEqualsExcluding(project, testProject, projectExcludes);
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void successfulBuild() {
		Status status = testBuild.getStatus();
		if (status != Status.SUCCESS) {
			throw new AssertionError(String.format(
					"Build status %s != %s.  Check getBuildLog output.", status,
					Status.SUCCESS));
		}
	}

	@Test(dependsOnMethods = "successfulBuild", groups = "project")
	public void reRunBuild() throws ApiException {
		Build origBuild = testBuild;
		int buildId = origBuild.getBuildId();

		try {
			ReRunBuildRequest reRunIncompleteBuild = new ReRunBuildRequest()
					.buildId(buildId)
					.reRunIncomplete(true);
			buildApi.reRunBuild(reRunIncompleteBuild);
			fail("Expected ApiException due to reRunIncomplete: true");
		} catch (ApiExceptionWithModel ex) {
			assertEquals(ex.getCode(), 500);
			assertEquals(
					ex.getResponseModel().getMessage(),
					"No failed or cancelled jobs in build with ID " + buildId);
		}

		ReRunBuildRequest reRunBuild = new ReRunBuildRequest()
				.buildId(buildId);
		Build rebuild = buildApi.reRunBuild(reRunBuild);
		testRebuild = rebuild;
		assertEquals(rebuild.getCommitId(), origBuild.getCommitId());
		assertEquals(rebuild.getProjectId(), origBuild.getProjectId());
		assertNotEquals(rebuild.getBuildId(), origBuild.getBuildId());
		assertNotEquals(rebuild.getVersion(), origBuild.getVersion());
	}

	// This is not really a test, but is used for synchronization by other tests
	@Test(dependsOnMethods = "reRunBuild", groups = "project")
	public void waitForRebuild() throws ApiException, InterruptedException {
		ProjectBuildResults projectBuild = this.waitForBuild(
				testProject.getAccountName(),
				testProject.getSlug(),
				testRebuild.getVersion());
		Build build = projectBuild.getBuild();
		testRebuild = build;
		assertNotNull(build.getFinished());
		Project project = projectBuild.getProject();
		assertModelEqualsExcluding(project, testProject, projectExcludes);
	}

	@Test(dependsOnMethods = "waitForRebuild", groups = "project")
	public void successfulRebuild() {
		Status status = testRebuild.getStatus();
		if (status != Status.SUCCESS) {
			throw new AssertionError(String.format(
					"Rebuild status %s != %s.  Check getBuildLog output.", status,
					Status.SUCCESS));
		}
	}

	// Note: Will 404 for projects with no build
	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getProjectStatusBadge()
			throws ApiException, FileNotFoundException, IOException {
		String statusBadgeId = testProjectConfig.getStatusBadgeId();
		File pngBadge = projectApi.getProjectStatusBadge(statusBadgeId, false,
				false, null, null, null);
		assertTrue(pngBadge.exists());
		long pngSize = pngBadge.length();
		try {
			assertIsPng(pngBadge.toPath());
		} finally {
			pngBadge.delete();
		}

		File retinaBadge = projectApi.getProjectStatusBadge(statusBadgeId,
				false,
				true, null, null, null);
		assertTrue(retinaBadge.exists());
		long retinaSize = retinaBadge.length();
		try {
			assertIsPng(retinaBadge.toPath());

			assertTrue(retinaSize > pngSize);
		} finally {
			retinaBadge.delete();
		}

		String uid = new BigInteger(128, new Random()).toString(36);
		File svgBadge = projectApi.getProjectStatusBadge(statusBadgeId, true,
				false,
				uid, uid, uid);
		assertTrue(svgBadge.exists());
		try {
			assertIsSvg(svgBadge.toPath());

			String svgBadgeText = new String(
					Files.readAllBytes(svgBadge.toPath()));
			assertTrue(svgBadgeText.contains(uid));
		} finally {
			svgBadge.delete();
		}
	}

	@Test
	public void getPublicProjectStatusBadge()
			throws ApiException, FileNotFoundException, IOException {
		File pngBadge = projectApi.getPublicProjectStatusBadge(
				TEST_BADGE_PROVIDER, TEST_BADGE_ACCOUNT, TEST_BADGE_SLUG, null,
				false, false, null, null, null);
		assertTrue(pngBadge.exists());
		long pngSize = pngBadge.length();
		try {
			assertIsPng(pngBadge.toPath());
		} finally {
			pngBadge.delete();
		}

		File retinaBadge = projectApi.getPublicProjectStatusBadge(
				TEST_BADGE_PROVIDER, TEST_BADGE_ACCOUNT, TEST_BADGE_SLUG, null,
				false, true, null, null, null);
		assertTrue(retinaBadge.exists());
		long retinaSize = retinaBadge.length();
		try {
			assertIsPng(retinaBadge.toPath());
			assertTrue(retinaSize > pngSize);
		} finally {
			retinaBadge.delete();
		}

		String uid = new BigInteger(128, new Random()).toString(36);
		File svgBadge = projectApi.getPublicProjectStatusBadge(
				TEST_BADGE_PROVIDER, TEST_BADGE_ACCOUNT, TEST_BADGE_SLUG, null,
				true, false, uid, uid, uid);
		assertTrue(svgBadge.exists());
		try {
			assertIsSvg(svgBadge.toPath());

			String svgBadgeText = new String(
					Files.readAllBytes(svgBadge.toPath()));
			assertTrue(svgBadgeText.contains(uid));
		} finally {
			svgBadge.delete();
		}

		String branchUid = new BigInteger(128, new Random()).toString(36);
		File svgBranchBadge = projectApi.getPublicProjectStatusBadge(
				TEST_BADGE_PROVIDER, TEST_BADGE_ACCOUNT, TEST_BADGE_SLUG,
				"master", true, false, branchUid, branchUid, branchUid);
		assertTrue(svgBranchBadge.exists());
		try {
			assertIsSvg(svgBranchBadge.toPath());

			String svgBranchBadgeText = new String(
					Files.readAllBytes(svgBranchBadge.toPath()));
			assertTrue(svgBranchBadgeText.contains(branchUid));
		} finally {
			svgBranchBadge.delete();
		}
	}

	@Test(dependsOnMethods = "successfulBuild", groups = "project")
	public void getBuildArtifacts() throws ApiException {
		String jobId = testBuild.getJobs().get(0).getJobId();
		List<ArtifactModel> artifacts = buildApi.getBuildArtifacts(jobId);
		for (ArtifactModel artifact : artifacts) {
			String fileName = artifact.getFileName();
			if (fileName.indexOf('/') >= 0) {
				testArtifactPath = artifact;
			} else {
				testArtifact = artifact;
			}
		}
		assertNotNull(testArtifact);
		assertNotNull(testArtifactPath);
	}

	@Test(dependsOnMethods = "getBuildArtifacts", groups = "project")
	public void getBuildArtifact() throws ApiException {
		String jobId = testBuild.getJobs().get(0).getJobId();
		String testName = testArtifact.getFileName();
		File artifact1 = buildApi.getBuildArtifact(jobId, testName);
		assertTrue(artifact1.exists());
		try {
			assertNotEquals(artifact1.length(), 0L);
		} finally {
			artifact1.delete();
		}

		String testPath = testArtifactPath.getFileName();
		File artifact2 = buildApi.getBuildArtifact(jobId, testPath);
		assertTrue(artifact2.exists());
		try {
			assertNotEquals(artifact2.length(), 0L);
		} finally {
			artifact2.delete();
		}
	}

	@Test(dependsOnMethods = "getBuildArtifacts", groups = "project")
	public void getProjectArtifact() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		String testName = testArtifact.getFileName();
		File artifact1 = projectApi.getProjectArtifact(accountName, slug,
				testName, null, null, null, null, null);
		assertTrue(artifact1.exists());
		try {
			assertNotEquals(artifact1.length(), 0L);
		} finally {
			artifact1.delete();
		}

		String testPath = testArtifactPath.getFileName();
		File artifact2 = projectApi.getProjectArtifact(accountName, slug,
				testPath, null, null, null, null, null);
		assertTrue(artifact2.exists());
		try {
			assertNotEquals(artifact2.length(), 0L);
		} finally {
			artifact2.delete();
		}
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
	public void getProjectLastBuild() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectBuildResults lastProjectBuild = projectApi
				.getProjectLastBuild(accountName, slug);
		Project project = lastProjectBuild.getProject();
		assertModelEqualsExcluding(project, testProject, projectExcludes);
		Build lastBuild = lastProjectBuild.getBuild();
		assertModelEqualsExcluding(lastBuild, testBuild, buildJobExcludes);
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getProjectLastBuildBranch() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		String branch = testBuild.getBranch();
		ProjectBuildResults branchBuild = projectApi
				.getProjectLastBuildBranch(accountName, slug, branch);
		Project project = branchBuild.getProject();
		assertModelEqualsExcluding(project, testProject, projectExcludes);
		Build build = branchBuild.getBuild();
		assertModelEqualsExcluding(build, testBuild, buildJobExcludes);
	}

	@Test(dependsOnMethods = "waitForBuild", groups = "project")
	public void getProjectHistory() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();
		ProjectHistory history = projectApi.getProjectHistory(accountName, slug,
				10, null, null);

		Project project = history.getProject();
		assertModelEqualsExcluding(project, testProject, projectExcludes);

		List<Build> builds = history.getBuilds();
		assertNotEquals(builds.size(), 0);
		Build lastBuild = builds.get(0);
		// This operation does not include the jobs property
		assertTrue(lastBuild.getJobs().isEmpty());
		assertModelEqualsExcluding(lastBuild, testBuild, buildExcludeJobs);
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
		Deployment deployment = deploymentApi.startDeployment(deploymentStart);
		DeploymentEnvironment environment = deployment.getEnvironment();
		normalizeSecurity(environment);
		testDeployment = deployment;

		Build build = deployment.getBuild();
		// "jobs" property is not returned by this operation
		assertTrue(build.getJobs().isEmpty());
		assertModelEqualsExcluding(build, testBuild, buildExcludeJobs);
		assertModelAgreesExcluding(environment, testEnvironment,
				excludeUpdated);
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

		List<EnvironmentDeploymentModel> deployments = envDeps.getDeployments();
		assertEquals(deployments.size(), 1);
		EnvironmentDeploymentModel deployment = deployments.get(0);
		assertModelAgrees(deployment, testDeployment);
		assertModelAgrees(deployment.getProject(), testProject);
	}

	@Test(dependsOnMethods = "waitForDeployment", groups = { "environment",
			"project" })
	public void getProjectDeployments() throws ApiException {
		String accountName = testProject.getAccountName();
		String slug = testProject.getSlug();

		// getProjectDeployments is flaky and often returns 500 due to timeout.
		// The recordsNumber value used by the website often changes.
		// Document observed changes and ignore timeout errors.
		//
		// Note: On 2018-06-05 started failing for recordsNumber < 11.
		//       Use 12 as the website currently does.
		// Note: On 2018-09-03 started failing for recordsNumber == 12.
		//       Use 10 as the website currently does.
		// Note: On 2018-09-20 started failing for recordsNumber == 10.
		//       Use 11 as the website currently does.
		// Note: On 2018-10-31 timeout errors.
		//       Website using 10 again.
		ProjectDeploymentsResults projectDeployments;
		try {
			projectDeployments =
					projectApi.getProjectDeployments(accountName, slug, 11);
		} catch (ApiExceptionWithModel ex) {
			String errMsg = ex.getResponseModel().getMessage();
			if (Pattern.matches("(?i)\\btime\\s*out\\b", errMsg))
			{
				throw new SkipException("getProjectDeployments timeout", ex);
			}

			throw ex;
		}

		Project project = projectDeployments.getProject();
		assertModelAgreesExcluding(project, testProject, projectExcludes);
		List<ProjectDeploymentModel> environmentDeployments = projectDeployments
				.getDeployments();
		assertEquals(environmentDeployments.size(), 1);
		ProjectDeploymentModel environmentDeployment = environmentDeployments
				.get(0);
		assertModelAgrees(environmentDeployment, testDeployment);
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

class RoleNameComparator implements Comparator<Role> {
	@Override
	public int compare(Role role1, Role role2) {
		return role1.getName().compareTo(role2.getName());
	}

}
