package net.hockeyapp.jenkins.migrators;

import hockeyapp.HockeyappRecorder;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Plugin;
import hudson.model.AbstractProject;
import hudson.model.listeners.ItemListener;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.utils.CredentialUtils;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class TokenMigrator extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(TokenMigrator.class.getName());

    @Override
    public void onLoaded() {
        super.onLoaded();

        final Jenkins jenkins = Jenkins.getInstance();
        final HockeyappRecorder.DescriptorImpl hockeyappRecorderDescriptor = jenkins.getDescriptorByType(HockeyappRecorder.DescriptorImpl.class);
        final Plugin hockeyapp = jenkins.getPlugin("hockeyapp"); // Gets the currently installed plugin by name

        if (hockeyapp == null) {
            return; // No point trying to migrate if we don't have something to migrate to
        }

        for (AbstractProject<?, ?> item : jenkins.getAllItems(AbstractProject.class)) {
            final HockeyappRecorder hockeyappRecorder = item.getPublishersList().get(HockeyappRecorder.class);

            BulkChange bc = new BulkChange(item);
            if (hockeyappRecorder != null) {
                final CredentialUtils credentialUtils = CredentialUtils.getInstance();
                try {
                    LOGGER.log(Level.FINER, "Attempting to migrate credentials for job: {0}", item.getFullDisplayName());
                    credentialUtils.migrateJobCredential(item, hockeyappRecorder);
                    LOGGER.log(Level.FINER, "Successfully migrated credential for job: {0}", item.getFullDisplayName());
                    item.save();
                    bc.commit();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Unable to save configuration for job: " + item.getFullName(), e);
                } finally {
                    bc.abort();
                }
            }
        }

        hockeyappRecorderDescriptor.save();
    }
}
