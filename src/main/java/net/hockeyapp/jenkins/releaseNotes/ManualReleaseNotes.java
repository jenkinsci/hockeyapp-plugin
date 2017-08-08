package net.hockeyapp.jenkins.releaseNotes;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;

public class ManualReleaseNotes extends RadioButtonSupport {

    @Exported
    private String releaseNotes;

    @Exported
    private boolean isMarkdown;

    @DataBoundConstructor
    public ManualReleaseNotes(String releaseNotes, boolean isMarkdown) {
        this.releaseNotes = Util.fixEmptyAndTrim(releaseNotes);
        this.isMarkdown = isMarkdown;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public boolean isMarkdown() {
        return isMarkdown;
    }

    public boolean getIsMarkdown() {
        return isMarkdown;
    }

    public Descriptor<RadioButtonSupport> getDescriptor() {
        final Jenkins instance = Jenkins.getInstance();
        return instance == null ? null : instance.getDescriptorOrDie(this.getClass());
    }

    @Extension
    public static class DescriptorImpl extends RadioButtonSupportDescriptor<ManualReleaseNotes> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Input Release Notes";
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckReleaseNotes(@QueryParameter String value) throws IOException, ServletException {
            if (value.isEmpty()) {
                return FormValidation.error("You must enter Release Notes.");
            } else {
                return FormValidation.ok();
            }

        }

    }
}
