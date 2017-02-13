package net.hockeyapp.jenkins;

import hudson.Util;
import org.kohsuke.stapler.DataBoundConstructor;

public class OldVersionHolder {
    public String numberOldVersions;
    // Defaults per https://support.hockeyapp.net/kb/api/api-versions#delete-multiple-versions
    public String sortOldVersions = "version";
    public String strategyOldVersions = "purge";

    @DataBoundConstructor
    public OldVersionHolder(String numberOldVersions, String sortOldVersions, String strategyOldVersions) {
        this.numberOldVersions = Util.fixEmptyAndTrim(numberOldVersions);
        this.sortOldVersions = Util.fixEmptyAndTrim(sortOldVersions);
        this.strategyOldVersions = Util.fixEmptyAndTrim(strategyOldVersions);
    }
}
