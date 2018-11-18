package hockeyapp;

import hockeyapp.builder.HockeyappApplicationBuilder;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import net.hockeyapp.jenkins.releaseNotes.ManualReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static hockeyapp.builder.HockeyappApplicationBuilder.FILE_PATH;

public class FreestyleTest extends ProjectTest {

    private FreeStyleProject project;

    @SuppressWarnings("ConstantConditions")
    @Before
    public void before() throws Exception {
        super.before();
        project = jenkinsRule.createFreeStyleProject();
        project.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build,
                                   Launcher launcher,
                                   BuildListener listener)
                    throws InterruptedException, IOException {
                build.getWorkspace().child(FILE_PATH).write(IPA_CONTENTS, "UTF-8");
                return true;
            }
        });
    }

    @Test
    public void should_SendUploadRequest_WithDefaults_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder().create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, postRequestedFor(urlEqualTo(HOCKEY_APP_UPLOAD_URL))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1))
                .withRequestBody(privateFormData(false)));
        failOnUnmatchedRequests();
    }

    @Test
    public void should_SendUploadRequest_WithOldVersion_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setOldVersionHolder(new HockeyappApplication.OldVersionHolder(
                        "5",
                        "version",
                        "purge"))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, postRequestedFor(urlEqualTo(HOCKEY_APP_UPLOAD_URL))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1))
                .withRequestBody(privateFormData(false)));
        mockHockeyAppServer.verify(1, postRequestedFor(
                urlEqualTo(HOCKEY_APP_DELETE_URL))
                .withRequestBody(equalTo("keep=5&sort=version&strategy=purge")));
        failOnUnmatchedRequests();
    }

    @Test
    public void should_SendUploadRequest_WithManualReleaseNotes_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, postRequestedFor(urlEqualTo(HOCKEY_APP_UPLOAD_URL))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1))
                .withRequestBody(privateFormData(false))
                .withRequestBody(releaseNotesFormData())
                .withRequestBody(releaseNotesTypeFormData()));
        failOnUnmatchedRequests();
    }

    @Test
    public void should_CreateConfigurationAction_When_BuildEndsInSuccess() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        assertConfigurationLinkActionIsCreated(build);
        failOnUnmatchedRequests();
    }

    @Test
    public void should_CreateInstallationAction_When_BuildEndsInSuccess() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        assertInstallationLinkActionIsCreated(build);
        failOnUnmatchedRequests();
    }

    @Test
    public void should_Not_CreateInstallationAction_When_APIKeyHasNoPermissionToRelease() throws Exception {
        // Given
        apiKeyHasNoUploadPermission();
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        assertInstallationLinkActionIsNotCreated(build);
        failOnUnmatchedRequests();
    }

    @Test
    public void should_SendUploadRequest_When_VersionCreationIsSelected_And_VersionIsNotSpecified() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setUploadMethod(new VersionCreation(APP_ID))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, postRequestedFor(urlEqualTo(HOCKEY_VERSION_UPLOAD_NEW_URL))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1)));
        failOnUnmatchedRequests();
    }

    @Test
    public void should_SendUploadRequest_When_VersionCreationIsSelected_And_VersionIsSpecified_1() throws Exception {
        // Given
        final String version = "1";
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setUploadMethod(new VersionCreation(APP_ID, version))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, putRequestedFor(urlEqualTo(HOCKEY_VERSION_UPLOAD_EXISTING_BASE_URL + version))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1)));
        failOnUnmatchedRequests();
    }

    @Test
    public void should_SendUploadRequst_When_VersionCreationIsSelected_And_VersionIsSpecified_2() throws Exception {
        // Given
        final String version = "2";
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setUploadMethod(new VersionCreation(APP_ID, version))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        addFreeStyleJob(applications);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, putRequestedFor(urlEqualTo(HOCKEY_VERSION_UPLOAD_EXISTING_BASE_URL + version))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1)));
        failOnUnmatchedRequests();
    }

    private void addFreeStyleJob(List<HockeyappApplication> applications) {
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        hockeyappRecorder.setBaseUrl("http://localhost:" + mockHockeyAppServer.port());
        project.getPublishersList().add(hockeyappRecorder);
    }
}
