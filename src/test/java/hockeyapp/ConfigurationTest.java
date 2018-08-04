package hockeyapp;

import hockeyapp.builder.HockeyappApplicationBuilder;
import hockeyapp.builder.HockeyappRecorderBuilder;
import hudson.model.FreeStyleProject;
import net.hockeyapp.jenkins.releaseNotes.ChangelogReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.Collections;

public class ConfigurationTest {
    @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void roundTripPublisherConfiguration() throws Exception {
        roundtripTest(new HockeyappRecorderBuilder()
                .setApplications(Collections.singletonList(new HockeyappApplicationBuilder().create()))
                .create());
    }

    @Test
    public void roundTripPublisherConfigurationWithOldVersionHolder() throws Exception {
        roundtripTest(new HockeyappRecorderBuilder()
                .setApplications(Collections.singletonList(
                        new HockeyappApplicationBuilder()
                                .setOldVersionHolder(new HockeyappApplication.OldVersionHolder(
                                        "5",
                                        "version",
                                        "purge"
                                ))
                                .create()))
                .create());
    }

    @Test
    public void roundTripPublisherConfigurationWithMultipleApps() throws Exception {
        roundtripTest(new HockeyappRecorderBuilder()
                .setApplications(Arrays.asList(
                        new HockeyappApplicationBuilder()
                                .setReleaseNotesMethod(new ChangelogReleaseNotes())
                                .create(),
                        new HockeyappApplicationBuilder()
                                .setUploadMethod(new AppCreation(false))
                                .create()))
                .create());
    }

    private void roundtripTest(HockeyappRecorder original) throws Exception {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        project.getPublishersList().add(original);
        jenkinsRule.submit(jenkinsRule
                .createWebClient()
                .getPage(project, "configure")
                .getFormByName("config"));
        jenkinsRule.assertEqualDataBoundBeans(
                original,
                project.getPublishersList().get(HockeyappRecorder.class));
    }
}
