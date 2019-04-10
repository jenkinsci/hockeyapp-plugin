package net.hockeyapp.jenkins.utils;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.domains.SchemeSpecification;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hockeyapp.HockeyappApplication;
import hockeyapp.HockeyappRecorder;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities to help with credential related tasks, such as credential lookup and migration of insecurely
 * stored credentials.
 * <p>
 * Based on the same class from https://github.com/jenkinsci/hipchat-plugin
 */
public class CredentialUtils {

    private static CredentialUtils instance;
    private final String DEFAULT_TOKEN_NAME = "hockeyapp-api-token";

    private CredentialUtils() {
    }

    public static CredentialUtils getInstance() {
        if (instance == null) {
            synchronized (CredentialUtils.class) {
                if (instance == null) {
                    instance = new CredentialUtils();
                }
            }
        }
        return instance;
    }

    /**
     * Retrieves the UI model object containing all acceptable credentials. This method can operate in two modes.
     *
     * <ul>
     * <li>When item is null: in this case the credentials will be looked up globally. In this case the assumption
     * is that we are displaying the credential dropdown on the global config page.</li>
     * <li>When item is not null: in this case the credentials will be looked up within the context of the job. In
     * this case the assumption is that we are displaying the credential dropdown on the job config page.</li>
     * </ul>
     *
     * @param item         The context (job) to use to find the the credentials. May be null. In job config mode, the current
     *                     value of the credential setting will be extracted from this item.
     * @param credentialId In global config mode, use this as the currently selected credential.
     * @param server       The URL to the server to ensure that we find the credentials under the right security
     *                     domain.
     * @return The UI model containing all matching credentials, or only the current selection if the user does not have
     * the right set of permissions.
     */
    public ListBoxModel getAvailableCredentials(@CheckForNull final Item item, final String credentialId, final String server) {
        StandardListBoxModel model = new StandardListBoxModel();

        if (!hasPermissionToConfigureCredentials(item)) {
            return new StandardListBoxModel().includeCurrentValue(credentialId);
        }

        Authentication credentialAuthentication = item instanceof Queue.Task ?
                Tasks.getAuthenticationOf((Queue.Task) item) : ACL.SYSTEM;

        if (item == null) {
            model.includeAs(credentialAuthentication, Jenkins.getInstance(), StringCredentials.class, requirements(server));
            model.includeEmptyValue();
        } else {
            model.includeAs(credentialAuthentication, item, StringCredentials.class, requirements(server));
        }

        if (credentialId != null) {
            model.includeCurrentValue(credentialId);
        }

        return model;
    }

    /**
     * Migrates the credential stored in global config from the old insecure format to the Credential system.
     *
     * @param descriptor The descriptor of this plugin.
     * @throws IOException If there was an error whilst migrating the credential.
     */
    public void migrateGlobalCredential(HockeyappRecorder.DescriptorImpl descriptor) throws IOException {
        final String defaultToken = descriptor.getDefaultToken();
        final String defaultHockeyUrl = descriptor.getDefaultBaseUrl();

        if (defaultToken == null) {
            return; // No point migrating a token that doesn't exist.
        }

        List<StringCredentials> existingCredentials = CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                Jenkins.getInstance(),
                ACL.SYSTEM,
                requirements(defaultHockeyUrl)
        );

        String credentialId = storeCredential(existingCredentials, defaultHockeyUrl, defaultToken);

        descriptor.setDefaultToken(null);
        descriptor.setCredentialId(credentialId);
    }

    /**
     * Migrates the credential stored in a job config from the old insecure format to the Credential system.
     *
     * @param item              The job where the plugin is configured.
     * @param hockeyappRecorder The plugin instance corresponding to this job.
     * @throws IOException If there was an error whilst migrating the credential.
     */
    @SuppressWarnings("deprecation")
    public void migrateJobCredential(final Item item, final HockeyappRecorder hockeyappRecorder) throws IOException {
        final String baseUrl = hockeyappRecorder.getBaseUrl();

        for (HockeyappApplication hockeyappApplication : hockeyappRecorder.getApplications()) {
            final String apiToken = hockeyappApplication.apiToken;
            if (apiToken == null) {
                continue; // No point migrating a token that doesn't exist.
            }

            final List<StringCredentials> existingCredentials = CredentialsProvider.lookupCredentials(
                    StringCredentials.class,
                    item,
                    ACL.SYSTEM,
                    requirements(baseUrl)
            );

            final String credentialId = storeCredential(existingCredentials, baseUrl, apiToken);

            hockeyappApplication.apiToken = null;
            hockeyappApplication.setCredentialId(credentialId);
        }
    }

    private String storeCredential(final List<StringCredentials> existingCredentials, final String baseUrl, final String apiToken) throws IOException {
        final List<String> existingIds = new ArrayList<>();

        for (StringCredentials credential : existingCredentials) {
            existingIds.add(credential.getId());
            if (credential.getId().startsWith(DEFAULT_TOKEN_NAME) && apiToken.equals(Secret.toString(credential.getSecret()))) {
                // If we have already stored a credential for this api token then return the credential's id.
                return credential.getId();
            }
        }

        final CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
        final String id = generateCredentialId(existingIds);
        final String description = String.format("%s. Automatically migrated. Identify usage and apply a more meaningful id and description.", id);
        final BaseStandardCredentials credential = new StringCredentialsImpl(CredentialsScope.GLOBAL, id, description, Secret.fromString(apiToken));

        if (store.isDomainsModifiable()) {
            final String domainName = "HockeyApp";
            final String domainDescription = "Automatically created domain. Identify usage and apply a more meaningful name and description.";
            final Domain domain = store.getDomainByName(baseUrl);
            if (domain == null) {
                // If we don't have a domain in the store for our credential, create a domain and the credential at the same time.
                final List<DomainSpecification> specs = new ArrayList<>();
                specs.add(new HostnameSpecification(baseUrl, null));
                specs.add(new SchemeSpecification("https"));
                final Domain newDomain = new Domain(domainName, domainDescription, specs);
                store.addDomain(newDomain, credential);
            } else {
                // Otherwise we have a domain so add the credential.
                store.addCredentials(domain, credential);
            }
        } else {
            // Otherwise just add it to the global domain and be done with it.
            store.addCredentials(Domain.global(), credential);
        }

        return credential.getId();
    }

    private boolean hasPermissionToConfigureCredentials(@CheckForNull final Item item) {
        if (item == null) {
            return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
        }

        final boolean canReadAndUse = item.hasPermission(Item.EXTENDED_READ) && item.hasPermission(CredentialsProvider.USE_ITEM);
        final boolean canConfigure = item.hasPermission(Item.CONFIGURE);

        return canReadAndUse || canConfigure;

    }

    private String generateCredentialId(final List<String> existingIds) {
        // Users may already have a credential id with this name so try and account for this.
        String candidate = DEFAULT_TOKEN_NAME;
        int i = 2;
        while (existingIds.contains(candidate)) {
            candidate = DEFAULT_TOKEN_NAME + "-" + i++;
        }
        return candidate;
    }

    private List<DomainRequirement> requirements(final String baseUrl) {
        return URIRequirementBuilder.create().withHostname(baseUrl).build();
    }
}
