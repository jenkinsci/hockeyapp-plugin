package hockeyapp;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hockeyapp.builder.HockeyappApplicationBuilder;
import hudson.model.FreeStyleProject;
import net.hockeyapp.jenkins.releaseNotes.ChangelogReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfigurationTest {
    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private FreeStyleProject project;

    @Test
    public void should_ConfigureWithDefaults_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder().create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    @Test
    public void should_ConfigureWithOlderVersion_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder()
                .setOldVersionHolder(new HockeyappApplication.OldVersionHolder(
                        "5",
                        "version",
                        "purge"
                ))
                .create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    @Test
    public void should_ConfigureMultipleApps_Success() throws Exception {
        // Given
        final List<HockeyappApplication> applications = Arrays.asList(
                new HockeyappApplicationBuilder()
                        .setReleaseNotesMethod(new ChangelogReleaseNotes())
                        .create(),
                new HockeyappApplicationBuilder()
                        .setUploadMethod(new AppCreation(false))
                        .create());
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    @Test
    public void should_ConfigureWithDebugMode_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder().create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        hockeyappRecorder.setDebugMode(true);
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    @Test
    public void should_ConfigureWithFailGracefully_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder().create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        hockeyappRecorder.setFailGracefully(true);
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    @Test
    public void should_ConfigureWithBaseUrl_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder().create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        hockeyappRecorder.setBaseUrl("http://fake.url:1234");
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    @Test
    public void should_ConfigureWithBaseUrl_AndDebugMode_AndFailGracefully_Success() throws Exception {
        // Given
        final HockeyappApplication hockeyappApplication = new HockeyappApplicationBuilder().create();
        final List<HockeyappApplication> applications = Collections.singletonList(hockeyappApplication);
        final HockeyappRecorder hockeyappRecorder = new HockeyappRecorder(applications);
        hockeyappRecorder.setBaseUrl("http://fake.url:1234");
        hockeyappRecorder.setDebugMode(true);
        hockeyappRecorder.setFailGracefully(true);
        final HtmlForm freeStyleJob = configureFreeStyleJob(hockeyappRecorder);

        // When
        jenkinsRule.submit(freeStyleJob);

        // Then
        jenkinsRule.assertEqualDataBoundBeans(hockeyappRecorder, project.getPublishersList().get(HockeyappRecorder.class));
    }

    private HtmlForm configureFreeStyleJob(HockeyappRecorder hockeyappRecorder) throws IOException, SAXException {
        project = jenkinsRule.createFreeStyleProject();
        project.getPublishersList().add(hockeyappRecorder);
        return jenkinsRule.createWebClient().getPage(project, "configure").getFormByName("config");
    }
}
