package hockeyapp;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import net.hockeyapp.jenkins.releaseNotes.ChangelogReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.FileReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.ManualReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.NoReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.List;

@ExportedBean
public class HockeyappApplication implements Describable<HockeyappApplication> {
    public static final long SCHEMA_VERSION_NUMBER = 1L;

    @SuppressFBWarnings(value = {"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"}, justification = "Breaks binary compatibility if removed.")
    @XStreamAsAttribute
    @Deprecated
    public long schemaVersion; // TODO: Fix Findbugs gracefully.

    @Deprecated
    public transient String apiToken;
    @SuppressFBWarnings(value = {"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"}, justification = "Breaks binary compatibility if removed.")
    @Deprecated
    public String appId; // TODO: Fix Findbugs gracefully.
    public boolean notifyTeam;
    public String filePath;
    public String dsymPath;
    public String libsPath;
    public String tags;
    public String teams;
    public boolean mandatory;
    public boolean downloadAllowed;
    public OldVersionHolder oldVersionHolder;
    public RadioButtonSupport releaseNotesMethod;
    public RadioButtonSupport uploadMethod;
    private Secret apiTokenSecret;

    @Deprecated
    public HockeyappApplication(String apiToken, String appId, boolean notifyTeam,
                                String filePath, String dsymPath, String libsPath,
                                String tags, String teams, boolean mandatory,
                                boolean downloadAllowed, OldVersionHolder oldVersionHolder,
                                RadioButtonSupport releaseNotesMethod, RadioButtonSupport uploadMethod) {
        this(Secret.fromString(apiToken), notifyTeam, filePath, dsymPath, libsPath, tags, teams, mandatory,
                downloadAllowed, oldVersionHolder, releaseNotesMethod, uploadMethod);
    }

    @DataBoundConstructor
    public HockeyappApplication(Secret apiTokenSecret, boolean notifyTeam,
                                String filePath, String dsymPath, String libsPath,
                                String tags, String teams, boolean mandatory,
                                boolean downloadAllowed, OldVersionHolder oldVersionHolder,
                                RadioButtonSupport releaseNotesMethod, RadioButtonSupport uploadMethod) {
        this.apiTokenSecret = apiTokenSecret;
        this.notifyTeam = notifyTeam;
        this.filePath = Util.fixEmptyAndTrim(filePath);
        this.dsymPath = Util.fixEmptyAndTrim(dsymPath);
        this.libsPath = Util.fixEmptyAndTrim(libsPath);
        this.tags = Util.fixEmptyAndTrim(tags);
        this.downloadAllowed = downloadAllowed;
        this.oldVersionHolder = oldVersionHolder;
        this.releaseNotesMethod = releaseNotesMethod;
        this.uploadMethod = uploadMethod;
        this.teams = Util.fixEmptyAndTrim(teams);
        this.mandatory = mandatory;
    }

    @SuppressWarnings("unused")
    public RadioButtonSupport getReleaseNotesMethod() {
        return releaseNotesMethod;
    }

    @SuppressWarnings("unused")
    public RadioButtonSupport getUploadMethod() {
        return uploadMethod;
    }

    public String getNumberOldVersions() {
        return oldVersionHolder == null ? null : oldVersionHolder.numberOldVersions;
    }

    public String getSortOldVersions() {
        return oldVersionHolder == null ? null : oldVersionHolder.sortOldVersions;
    }

    public String getStrategyOldVersions() {
        return oldVersionHolder == null ? null : oldVersionHolder.strategyOldVersions;
    }

    @Override
    public Descriptor<HockeyappApplication> getDescriptor() {
        return new DescriptorImpl();
    }

    protected Object readResolve() {
        if (apiToken != null) {
            final Secret secret = Secret.fromString(apiToken);
            setApiTokenSecret(secret);
        }

        return this;
    }

    public Secret getApiTokenSecret() {
        return apiTokenSecret;
    }

    public void setApiTokenSecret(Secret apiTokenSecret) {
        this.apiTokenSecret = apiTokenSecret;
    }

    public static class OldVersionHolder {
        private String numberOldVersions;
        // Defaults per https://support.hockeyapp.net/kb/api/api-versions#delete-multiple-versions
        private String sortOldVersions = "version";
        private String strategyOldVersions = "purge";

        @DataBoundConstructor
        public OldVersionHolder(String numberOldVersions, String sortOldVersions, String strategyOldVersions) {
            this.numberOldVersions = Util.fixEmptyAndTrim(numberOldVersions);
            this.sortOldVersions = Util.fixEmptyAndTrim(sortOldVersions);
            this.strategyOldVersions = Util.fixEmptyAndTrim(strategyOldVersions);
        }

        public String getNumberOldVersions() {
            return numberOldVersions;
        }

        public String getSortOldVersions() {
            return sortOldVersions;
        }

        public String getStrategyOldVersions() {
            return strategyOldVersions;
        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<HockeyappApplication> {
        @Override
        public String getDisplayName() {
            return Messages.APPLICATION();
        }

        @SuppressWarnings("unused")
        public List<RadioButtonSupportDescriptor> getReleaseNotesMethodList() {
            List<RadioButtonSupportDescriptor> releaseNotesMethods = new ArrayList<RadioButtonSupportDescriptor>(4);
            final Jenkins activeInstance = Jenkins.getActiveInstance();
            releaseNotesMethods.add(
                    (RadioButtonSupportDescriptor) activeInstance.getDescriptorOrDie(NoReleaseNotes.class));
            releaseNotesMethods.add(
                    (RadioButtonSupportDescriptor) activeInstance.getDescriptorOrDie(ChangelogReleaseNotes.class));
            releaseNotesMethods.add(
                    (RadioButtonSupportDescriptor) activeInstance.getDescriptorOrDie(FileReleaseNotes.class));
            releaseNotesMethods.add(
                    (RadioButtonSupportDescriptor) activeInstance.getDescriptorOrDie(ManualReleaseNotes.class));
            return releaseNotesMethods;
        }

        @SuppressWarnings("unused")
        public List<RadioButtonSupportDescriptor> getUploadMethodList() {
            List<RadioButtonSupportDescriptor> uploadMethods = new ArrayList<RadioButtonSupportDescriptor>(2);
            Jenkins activeInstance = Jenkins.getActiveInstance();
            uploadMethods.add((RadioButtonSupportDescriptor) activeInstance.getDescriptorOrDie(AppCreation.class));
            uploadMethods.add((RadioButtonSupportDescriptor) activeInstance.getDescriptorOrDie(VersionCreation.class));
            return uploadMethods;
        }


        @SuppressWarnings("unused")
        public FormValidation doCheckApiToken(@QueryParameter String value) {
            if (value.isEmpty()) {
                final Jenkins activeInstance = Jenkins.getActiveInstance();

                HockeyappRecorder.DescriptorImpl hockeyappRecorderDescriptor =
                        (HockeyappRecorder.DescriptorImpl) activeInstance.getDescriptorOrDie(HockeyappRecorder.class);

                if (hockeyappRecorderDescriptor != null) {
                    Secret defaultToken = hockeyappRecorderDescriptor.getDefaultTokenSecret();

                    if (defaultToken != null) {
                        return FormValidation.warning("Default API Token is used.");
                    }
                }
                return FormValidation.errorWithMarkup(
                        "You must enter an <a href=\"https://rink.hockeyapp.net/manage/auth_tokens\">API Token</a>.");
            } else {
                return FormValidation.ok();
            }
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckNumberOldVersions(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must specify a positive Number.");
            } else {
                try {
                    int number = Integer.parseInt(value);
                    if (number > 0) {
                        return FormValidation.ok();
                    } else {
                        return FormValidation.error("You must specify a positive Number.");
                    }
                } catch (NumberFormatException e) {
                    return FormValidation.error("You must specify a positive Number.");
                }
            }
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckFilePath(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must enter a File Path.");
            } else {
                return FormValidation.ok();
            }
        }
    }


}
