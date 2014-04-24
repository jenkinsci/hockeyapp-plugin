package net.hockeyapp.jenkins.releaseNotes;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class NoReleaseNotes extends RadioButtonSupport {

    @DataBoundConstructor
    public NoReleaseNotes() {

    }

    public Descriptor<RadioButtonSupport> getDescriptor() {
        return Jenkins.getInstance() == null ? null : Jenkins.getInstance().getDescriptorOrDie(this.getClass());
    }

    @Extension
    public static class DescriptorImpl extends RadioButtonSupportDescriptor<NoReleaseNotes> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "No Release Notes";
        }

        @Override
        public RadioButtonSupport newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new NoReleaseNotes();
        }
    }

}