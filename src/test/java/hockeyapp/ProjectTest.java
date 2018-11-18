package hockeyapp;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import hudson.model.Action;
import hudson.model.Run;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static hockeyapp.builder.HockeyappApplicationBuilder.FILE_PATH;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class ProjectTest {

    static final String APP_ID = "appid";
    static final String IPA_CONTENTS = "Lorem Ipsum";

    static final String HOCKEY_APP_UPLOAD_URL = "/api/2/apps/upload";
    static final String HOCKEY_VERSION_UPLOAD_EXISTING_BASE_URL = "/api/2/apps/" + APP_ID + "/app_versions/";
    static final String HOCKEY_VERSION_UPLOAD_NEW_URL = "/api/2/apps/" + APP_ID + "/app_versions/upload";
    static final String HOCKEY_APP_DELETE_URL = "/api/2/apps/" + APP_ID + "/app_versions/delete";

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    @Rule
    public WireMockRule mockHockeyAppServer = new WireMockRule(options().dynamicPort());

    @Before
    public void before() throws Exception {
        // TODO: Template these responses for more flexibility.
        // Stub upload app
        mockHockeyAppServer.stubFor(post(urlEqualTo(HOCKEY_APP_UPLOAD_URL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody("{\n" +
                                "  \"title\": \"Android\",\n" +
                                "  \"bundle_identifier\": \"net.hockeyapp.jenkins.android\",\n" +
                                "  \"public_identifier\": \"" + APP_ID + "\",\n" +
                                "  \"platform\": \"Android\",\n" +
                                "  \"release_type\": 0,\n" +
                                "  \"custom_release_type\": null,\n" +
                                "  \"created_at\": \"2018-06-16T21:16:48Z\",\n" +
                                "  \"updated_at\": \"2018-06-16T21:16:51Z\",\n" +
                                "  \"featured\": false,\n" +
                                "  \"role\": 0,\n" +
                                "  \"id\": 788014,\n" +
                                "  \"config_url\": \"https://rink.hockeyapp.net/manage/apps/bar/app_versions/1\",\n" +
                                "  \"public_url\": \"https://rink.hockeyapp.net/apps/foo\",\n" +
                                "  \"minimum_os_version\": \"5.0\",\n" +
                                "  \"device_family\": null,\n" +
                                "  \"status\": 2,\n" +
                                "  \"visibility\": \"private\",\n" +
                                "  \"owner\": \"Foo Bar Inc\",\n" +
                                "  \"owner_token\": \"bar-baz\",\n" +
                                "  \"retention_days\": \"90\"\n" +
                                "}")));

        // Stub upload new version
        mockHockeyAppServer.stubFor(put(urlPathMatching(HOCKEY_VERSION_UPLOAD_EXISTING_BASE_URL+ "[0-9]+"))  // TODO: Grab version from request param
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody("{\n" +
                                "  \"title\": \"Android\",\n" +
                                "  \"bundle_identifier\": \"net.hockeyapp.jenkins.android\",\n" +
                                "  \"public_identifier\": \"" + APP_ID + "\",\n" +
                                "  \"platform\": \"Android\",\n" +
                                "  \"release_type\": 0,\n" +
                                "  \"custom_release_type\": null,\n" +
                                "  \"created_at\": \"2018-06-16T21:16:48Z\",\n" +
                                "  \"updated_at\": \"2018-06-16T21:16:51Z\",\n" +
                                "  \"featured\": false,\n" +
                                "  \"role\": 0,\n" +
                                "  \"id\": 788014,\n" +
                                "  \"config_url\": \"https://rink.hockeyapp.net/manage/apps/bar/app_versions/1\",\n" +
                                "  \"public_url\": \"https://rink.hockeyapp.net/apps/foo\",\n" +
                                "  \"minimum_os_version\": \"5.0\",\n" +
                                "  \"device_family\": null,\n" +
                                "  \"status\": 2,\n" +
                                "  \"visibility\": \"private\",\n" +
                                "  \"owner\": \"Foo Bar Inc\",\n" +
                                "  \"owner_token\": \"bar-baz\",\n" +
                                "  \"retention_days\": \"90\"\n" +
                                "}")));

        // Stub upload existing version
        mockHockeyAppServer.stubFor(post(urlEqualTo(HOCKEY_VERSION_UPLOAD_NEW_URL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody("{\n" +
                                "  \"title\": \"Android\",\n" +
                                "  \"bundle_identifier\": \"net.hockeyapp.jenkins.android\",\n" +
                                "  \"public_identifier\": \"" + APP_ID + "\",\n" +
                                "  \"platform\": \"Android\",\n" +
                                "  \"release_type\": 0,\n" +
                                "  \"custom_release_type\": null,\n" +
                                "  \"created_at\": \"2018-06-16T21:16:48Z\",\n" +
                                "  \"updated_at\": \"2018-06-16T21:16:51Z\",\n" +
                                "  \"featured\": false,\n" +
                                "  \"role\": 0,\n" +
                                "  \"id\": 788014,\n" +
                                "  \"config_url\": \"https://rink.hockeyapp.net/manage/apps/bar/app_versions/1\",\n" +
                                "  \"public_url\": \"https://rink.hockeyapp.net/apps/foo\",\n" +
                                "  \"minimum_os_version\": \"5.0\",\n" +
                                "  \"device_family\": null,\n" +
                                "  \"status\": 2,\n" +
                                "  \"visibility\": \"private\",\n" +
                                "  \"owner\": \"Foo Bar Inc\",\n" +
                                "  \"owner_token\": \"bar-baz\",\n" +
                                "  \"retention_days\": \"90\"\n" +
                                "}")));

        // Stub delete app
        mockHockeyAppServer.stubFor(post(urlEqualTo(HOCKEY_APP_DELETE_URL))
                .willReturn(aResponse()
                        .withStatus(204)));
    }

    void apiKeyHasNoUploadPermission() {
        mockHockeyAppServer.stubFor(post(urlEqualTo(HOCKEY_APP_UPLOAD_URL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody("{\n" +
                                "  \"title\": \"Android\",\n" +
                                "  \"bundle_identifier\": \"net.hockeyapp.jenkins.android\",\n" +
                                "  \"public_identifier\": \"" + APP_ID + "\",\n" +
                                "  \"platform\": \"Android\",\n" +
                                "  \"release_type\": 0,\n" +
                                "  \"custom_release_type\": null,\n" +
                                "  \"created_at\": \"2018-06-16T21:16:48Z\",\n" +
                                "  \"updated_at\": \"2018-06-16T21:16:51Z\",\n" +
                                "  \"featured\": false,\n" +
                                "  \"role\": 0,\n" +
                                "  \"id\": 788014,\n" +
                                "  \"config_url\": \"https://rink.hockeyapp.net/manage/apps/bar/app_versions/1\",\n" +
                                "  \"minimum_os_version\": \"5.0\",\n" +
                                "  \"device_family\": null,\n" +
                                "  \"status\": 2,\n" +
                                "  \"visibility\": \"private\",\n" +
                                "  \"owner\": \"Foo Bar Inc\",\n" +
                                "  \"owner_token\": \"bar-baz\",\n" +
                                "  \"retention_days\": \"90\"\n" +
                                "}")));
    }

    void failOnUnmatchedRequests() throws Exception {
        List<LoggedRequest> unmatchedRequests = mockHockeyAppServer.findUnmatchedRequests().getRequests();
        if (unmatchedRequests.size() != 0) {
            throw new Exception("Found unmatched requests: " + unmatchedRequests.toString());
        }
    }

    void assertBuildSuccessful(Run build) throws Exception {
        String log = FileUtils.readFileToString(build.getLogFile());
        assertThat(log, containsString("Uploading to HockeyApp..."));
        assertThat(log, containsString(FILE_PATH));
        jenkinsRule.assertBuildStatusSuccess(build);
    }

    void assertConfigurationLinkActionIsCreated(Run build) {
        final List<? extends Action> allActions = build != null ? build.getAllActions() : Collections.<Action>emptyList();
        boolean test = false;
        for (Action action : allActions) {
            if (action instanceof HockeyappBuildAction && action.getDisplayName().contentEquals(Messages.HOCKEYAPP_CONFIG_LINK())) {
                test = true;
            }
        }
        assertThat(test, is(true));
    }

    void assertInstallationLinkActionIsNotCreated(Run build) {
        final List<? extends Action> allActions = build != null ? build.getAllActions() : Collections.<Action>emptyList();
        boolean test = false;
        for (Action action : allActions) {
            if (action instanceof HockeyappBuildAction && action.getDisplayName().contentEquals(Messages.HOCKEYAPP_INSTALL_LINK())) {
                test = true;
            }
        }
        assertThat(test, is(not((true))));
    }

    void assertInstallationLinkActionIsCreated(Run build) {
        final List<? extends Action> allActions = build != null ? build.getAllActions() : Collections.<Action>emptyList();
        boolean test = false;
        for (Action action : allActions) {
            if (action instanceof HockeyappBuildAction && action.getDisplayName().contentEquals(Messages.HOCKEYAPP_INSTALL_LINK())) {
                test = true;
            }
        }
        assertThat(test, is(true));
    }

    StringValuePattern releaseNotesTypeFormData() {
        return containing("Content-Disposition: form-data; name=\"notes_type\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit");
    }

    StringValuePattern releaseNotesFormData() {
        return containing("Content-Disposition: form-data; name=\"notes\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                "releaseNotes");
    }

    StringValuePattern privateFormData(boolean value) {
        return containing("Content-Disposition: form-data; name=\"private\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Boolean.toString(value));
    }

    StringValuePattern statusFormData(int value) {
        return containing("Content-Disposition: form-data; name=\"status\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Integer.toString(value));
    }

    StringValuePattern notifyFormData(int value) {
        return containing("Content-Disposition: form-data; name=\"notify\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Integer.toString(value));
    }

    StringValuePattern mandatoryFormData(int value) {
        return containing("Content-Disposition: form-data; name=\"mandatory\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Integer.toString(value));
    }

    StringValuePattern ipaFormData() {
        return containing(
                "Content-Disposition: form-data; name=\"ipa\"; filename=\"test.ipa\"\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Transfer-Encoding: binary\r\n" +
                        "\r\n" +
                        IPA_CONTENTS);
    }
}
