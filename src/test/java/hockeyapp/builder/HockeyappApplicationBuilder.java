package hockeyapp.builder;

import hockeyapp.HockeyappApplication;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.releaseNotes.NoReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;

public class HockeyappApplicationBuilder {
    public static final String FILE_PATH = "test.ipa";

    private String apiToken = "API_TOKEN";
    private String credentialId = "CREDENTIAL_ID";
    private String appId = null;
    private boolean notifyTeam = false;
    private String filePath = FILE_PATH;
    private String dsymPath = null;
    private String libsPath = null;
    private String tags = null;
    private String teams = null;
    private boolean mandatory = false;
    private boolean downloadAllowed = false;
    private HockeyappApplication.OldVersionHolder oldVersionHolder = null;
    private RadioButtonSupport releaseNotesMethod = new NoReleaseNotes();
    private RadioButtonSupport uploadMethod = new AppCreation(true);

    public HockeyappApplicationBuilder setApiToken(String apiToken) {
        this.apiToken = apiToken;
        return this;
    }

    public HockeyappApplicationBuilder setCredentialId(String credentialId) {
        this.credentialId = credentialId;
        return this;
    }

    public HockeyappApplicationBuilder setAppId(String appId) {
        this.appId = appId;
        return this;
    }

    public HockeyappApplicationBuilder setNotifyTeam(boolean notifyTeam) {
        this.notifyTeam = notifyTeam;
        return this;
    }

    public HockeyappApplicationBuilder setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public HockeyappApplicationBuilder setDsymPath(String dsymPath) {
        this.dsymPath = dsymPath;
        return this;
    }

    public HockeyappApplicationBuilder setLibsPath(String libsPath) {
        this.libsPath = libsPath;
        return this;
    }

    public HockeyappApplicationBuilder setTags(String tags) {
        this.tags = tags;
        return this;
    }

    public HockeyappApplicationBuilder setTeams(String teams) {
        this.teams = teams;
        return this;
    }

    public HockeyappApplicationBuilder setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
        return this;
    }

    public HockeyappApplicationBuilder setDownloadAllowed(boolean downloadAllowed) {
        this.downloadAllowed = downloadAllowed;
        return this;
    }

    public HockeyappApplicationBuilder setOldVersionHolder(HockeyappApplication.OldVersionHolder oldVersionHolder) {
        this.oldVersionHolder = oldVersionHolder;
        return this;
    }

    public HockeyappApplicationBuilder setReleaseNotesMethod(RadioButtonSupport releaseNotesMethod) {
        this.releaseNotesMethod = releaseNotesMethod;
        return this;
    }

    public HockeyappApplicationBuilder setUploadMethod(RadioButtonSupport uploadMethod) {
        this.uploadMethod = uploadMethod;
        return this;
    }

    public HockeyappApplication create() {
        return new HockeyappApplication(credentialId, notifyTeam, filePath, dsymPath, libsPath, tags, teams, mandatory, downloadAllowed, oldVersionHolder, releaseNotesMethod, uploadMethod);
    }
}