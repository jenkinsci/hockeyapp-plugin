package hockeyapp;

import hockeyapp.builder.HockeyappApplicationBuilder;
import hockeyapp.builder.HockeyappRecorderBuilder;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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
    public void testFreestyleBuild() throws Exception {
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(new HockeyappApplicationBuilder().create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();

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
    public void testFreestyleBuildWithOldVersionHolder() throws Exception {
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setOldVersionHolder(new HockeyappApplication.OldVersionHolder(
                                        "5",
                                        "version",
                                        "purge"))
                                .create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();

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
    public void testFreestyleBuildWithManualReleaseNotes() throws Exception {
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                                .create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();

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
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                                .create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

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
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                                .create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

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
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setReleaseNotesMethod(new ManualReleaseNotes("releaseNotes", false))
                                .create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        assertInstallationLinkActionIsNotCreated(build);
        failOnUnmatchedRequests();
    }

    @Test
    public void testFreeStyleBuildWithSameVersion() throws Exception {
        // Given
        HockeyappRecorder hockeyappRecorder = new HockeyappRecorderBuilder()
                .setLocalhostBaseUrl(mockHockeyAppServer.port())
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setUploadMethod(new VersionCreation(APP_ID, "1"))
                                .create()))
                .create();
        project.getPublishersList().add(hockeyappRecorder);

        // When
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Then
        assertBuildSuccessful(build);
        mockHockeyAppServer.verify(1, putRequestedFor(urlEqualTo(HOCKEY_APP_VERSION_UPLOAD_BASE_URL))
                .withHeader("Content-Type", containing("multipart/form-data;"))
                .withRequestBody(ipaFormData())
                .withRequestBody(mandatoryFormData(0))
                .withRequestBody(notifyFormData(0))
                .withRequestBody(statusFormData(1)));
        failOnUnmatchedRequests();
    }
}
