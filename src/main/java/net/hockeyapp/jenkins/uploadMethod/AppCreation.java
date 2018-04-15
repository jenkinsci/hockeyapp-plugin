package net.hockeyapp.jenkins.uploadMethod;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;


public class AppCreation extends RadioButtonSupport {
    @Exported
    public boolean publicPage;

    @DataBoundConstructor
    public AppCreation(boolean publicPage) {
        this.publicPage = publicPage;
    }

    public Descriptor<RadioButtonSupport> getDescriptor() {
        final Jenkins instance = Jenkins.getInstance();
        return instance == null ? null : instance.getDescriptorOrDie(this.getClass());
    }

    @Extension
    public static class DescriptorImpl extends RadioButtonSupportDescriptor<AppCreation> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Upload App";
        }

        @Override
        public RadioButtonSupport newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            boolean isPublicPage = formData.getBoolean("publicPage");
            return new AppCreation(isPublicPage);
        }
    }
}
