package hockeyapp;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static hockeyapp.builder.HockeyappApplicationBuilder.FILE_PATH;

public class PipelineTest extends ProjectTest {
    private WorkflowJob workflowJob;

    @Before
    public void before() throws Exception {
        super.before();
        workflowJob = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflowJob");
    }

    @Test
    public void should_ConfigurePipeline_WithBaseUrl() throws Exception {
        // Given
        createHockeyappJob("[$class: 'HockeyappRecorder', \n" +
                "   applications: [\n" +
                "       [$class: 'HockeyappApplication', \n" +
                "        apiToken: 'API_TOKEN',\n" +
                "        filePath: '" + FILE_PATH + "',\n" +
                "        uploadMethod: [$class: 'AppCreation',\n" +
                "                       publicPage: true],\n" +
                "        releaseNotesMethod: [$class: 'NoReleaseNotes']\n" +
                "       ]\n" +
                "   ],\n" +
                "   baseUrl: 'http://localhost:" + mockHockeyAppServer.port() + "/'\n" +
                "]");

        // When
        WorkflowRun build = Objects.requireNonNull(workflowJob.scheduleBuild2(0)).get();

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
    public void should_ConfigurePipeline_WithOldVersionHolder() throws Exception {
        // Given
        createHockeyappJob("[$class: 'HockeyappRecorder', \n" +
                "   applications: [\n" +
                "       [$class: 'HockeyappApplication', \n" +
                "        apiToken: 'API_TOKEN',\n" +
                "        filePath: '" + FILE_PATH + "',\n" +
                "        uploadMethod: [$class: 'AppCreation',\n" +
                "                       publicPage: true],\n" +
                "        releaseNotesMethod: [$class: 'NoReleaseNotes']," +
                "        oldVersionHolder: [$class: 'hockeyapp.HockeyappApplication$OldVersionHolder'," +
                "                           numberOldVersions: '5'," +
                "                           sortOldVersions: 'version'," +
                "                           strategyOldVersions: 'purge'] \n" +
                "       ]\n" +
                "   ],\n" +
                "   baseUrl: 'http://localhost:" + mockHockeyAppServer.port() + "/'\n" +
                "]");

        // When
        WorkflowRun build = Objects.requireNonNull(workflowJob.scheduleBuild2(0)).get();

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

    private void createHockeyappJob(String hockeyAppInfo) {
        workflowJob.setDefinition(new CpsFlowDefinition(
                "node { \n" +
                        "writeFile file: 'test.ipa', text: '" + IPA_CONTENTS + "', encoding: 'UTF-8'\n" +
                        "step(" + hockeyAppInfo + ") \n" +
                        "}", true));
    }
}
