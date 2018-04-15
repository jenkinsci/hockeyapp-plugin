package net.hockeyapp.jenkins.releaseNotes;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class ChangelogReleaseNotes extends RadioButtonSupport {

    @DataBoundConstructor
    public ChangelogReleaseNotes() {

    }

    public Descriptor<RadioButtonSupport> getDescriptor() {
        final Jenkins instance = Jenkins.getInstance();
        return instance == null ? null : instance.getDescriptorOrDie(this.getClass());
    }

    @Extension
    public static class DescriptorImpl extends RadioButtonSupportDescriptor<ChangelogReleaseNotes> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Use Change Log";
        }

        @Override
        public RadioButtonSupport newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ChangelogReleaseNotes();
        }
    }

}