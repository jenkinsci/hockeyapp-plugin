package net.hockeyapp.jenkins;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class OldVersionHolder implements ExtensionPoint, Describable<OldVersionHolder> {

    @Exported
    public String numberOldVersions;
    // Defaults per https://support.hockeyapp.net/kb/api/api-versions#delete-multiple-versions
    @Exported
    public String sortOldVersions = "version";
    @Exported
    public String strategyOldVersions = "purge";

    @DataBoundConstructor
    public OldVersionHolder(String numberOldVersions, String sortOldVersions, String strategyOldVersions) {
        this.numberOldVersions = Util.fixEmptyAndTrim(numberOldVersions);
        this.sortOldVersions = Util.fixEmptyAndTrim(sortOldVersions);
        this.strategyOldVersions = Util.fixEmptyAndTrim(strategyOldVersions);
    }

    @Override
    public Descriptor<OldVersionHolder> getDescriptor() {
        return Jenkins.getInstance() == null ? null : Jenkins.getInstance().getDescriptorOrDie(this.getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<OldVersionHolder> implements Saveable {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Old Version Holder";
        }
    }
}