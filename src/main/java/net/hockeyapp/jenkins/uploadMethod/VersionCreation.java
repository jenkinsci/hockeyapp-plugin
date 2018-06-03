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
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;

public class VersionCreation extends RadioButtonSupport {

    @Exported
    private String appId;

    @DataBoundConstructor
    public VersionCreation(String appId) {
        this.appId = Util.fixEmptyAndTrim(appId);
    }

    public String getAppId() {
        return appId;
    }

    public Descriptor<RadioButtonSupport> getDescriptor() {
        final Jenkins instance = Jenkins.getInstance();
        return instance == null ? null : instance.getDescriptorOrDie(this.getClass());
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
        public FormValidation doCheckAppId(@QueryParameter String value) throws IOException, ServletException {
//            if(value.isEmpty()) {
//                return FormValidation.error("You must enter an App ID.");
//            } else if(value.length() != 32) {
//                return FormValidation.error("App ID length must be 32.");
//            } else {
//                if (value.matches("[0-9A-Fa-f]{32}")) {
//                    return FormValidation.ok();
//                } else {
//                    return FormValidation.warning("Check correctness of App ID.");
//                }
//            }
            if (value.isEmpty()) {
                return FormValidation.error("You must enter an App ID.");
            } else {
                return FormValidation.ok();
            }

        }
    }

}