package net.hockeyapp.jenkins.uploadMethod;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class VersionCreation extends RadioButtonSupport {

    @Exported
    private String appId;

    @Exported
    @CheckForNull
    private String versionCode;

    @DataBoundConstructor
    public VersionCreation(@Nonnull String appId) {
        this.appId = Util.fixNull(appId);
    }

    @Deprecated
    public VersionCreation(@Nonnull String appId, @CheckForNull String versionCode) {
        this.appId = Util.fixNull(appId);
        this.versionCode = Util.fixNull(versionCode);
    }

    @Nonnull
    public String getAppId() {
        return appId;
    }

    @CheckForNull
    public String getVersionCode() {
        return versionCode;
    }

    @DataBoundSetter
    public void setVersionCode(@CheckForNull String versionCode) {
        this.versionCode = Util.fixNull(versionCode);
    }

    public Descriptor<RadioButtonSupport> getDescriptor() {
        final Jenkins instance = Jenkins.getInstance();
        return instance.getDescriptorOrDie(this.getClass());
    }

    @Symbol("versionCreation")
    @Extension
    public static class DescriptorImpl extends RadioButtonSupportDescriptor<VersionCreation> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Upload Version";
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckAppId(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must enter an App ID.");
            } else {
                return FormValidation.ok();
            }

        }

        @SuppressWarnings("unused")
        public FormValidation doCheckVersionCode(@QueryParameter String value) {
            if (value.matches("[0-9]*") || value.equals("")) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning("Version code should be an integer value >0.");
            }
        }
    }

}