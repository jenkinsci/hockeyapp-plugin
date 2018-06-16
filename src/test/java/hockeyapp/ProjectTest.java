package hockeyapp;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import hudson.model.Run;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static hockeyapp.builder.HockeyappApplicationBuilder.FILE_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

public abstract class ProjectTest {

    static final String APP_ID = "appid";
    static final String IPA_CONTENTS = "Lorem Ipsum";

    static final String HOCKEY_APP_UPLOAD_URL = "/api/2/apps/upload";
    static final String HOCKEY_APP_DELETE_URL = "/api/2/apps/" + APP_ID + "/app_versions/delete";

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    @Rule
    public WireMockRule mockHockeyAppServer = new WireMockRule(options().dynamicPort());

    @Before
    public void before() throws Exception {
        mockHockeyAppServer.stubFor(post(urlEqualTo(HOCKEY_APP_UPLOAD_URL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody("{\"id\": 3702837409, \"public URL\": \"foobar\", \"public_identifier\": \"" + APP_ID + "\" }")));
        mockHockeyAppServer.stubFor(post(urlEqualTo(HOCKEY_APP_DELETE_URL))
                .willReturn(aResponse()
                        .withStatus(204)));
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

    StringValuePattern releaseNotesTypeFormData() {
        return containing("Content-Disposition: form-data; name=\"notes_type\"\r\n" +
                "Content-Type: text/plain; charset=US-ASCII\r\n" +
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
                "Content-Type: text/plain; charset=US-ASCII\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Boolean.toString(value));
    }

    StringValuePattern statusFormData(int value) {
        return containing("Content-Disposition: form-data; name=\"status\"\r\n" +
                "Content-Type: text/plain; charset=US-ASCII\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Integer.toString(value));
    }

    StringValuePattern notifyFormData(int value) {
        return containing("Content-Disposition: form-data; name=\"notify\"\r\n" +
                "Content-Type: text/plain; charset=US-ASCII\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" +
                Integer.toString(value));
    }

    StringValuePattern mandatoryFormData(int value) {
        return containing("Content-Disposition: form-data; name=\"mandatory\"\r\n" +
                "Content-Type: text/plain; charset=US-ASCII\r\n" +
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
