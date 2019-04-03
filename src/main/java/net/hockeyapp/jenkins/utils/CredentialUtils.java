package net.hockeyapp.jenkins.utils;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
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
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

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
     * Migrates the credential stored in a job config from the old insecure format to the Credential system.
     *
     * @param item              The job where the plugin is configured.
     * @param hockeyappRecorder The plugin instance corresponding to this job.
     * @throws IOException If there was an error whilst migrating the credential.
     */
    @SuppressWarnings("deprecation")
    public void migrateJobCredential(final Item item, final HockeyappRecorder hockeyappRecorder) throws IOException {
        final String baseUrl = hockeyappRecorder.getBaseUrl();
        final List<StringCredentials> existingCredentials = CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                item,
                ACL.SYSTEM,
                requirements(baseUrl)
        );

        for (HockeyappApplication hockeyappApplication : hockeyappRecorder.getApplications()) {
            final String apiToken = hockeyappApplication.apiToken;
            if (apiToken == null) {
                continue; // No point migrating a token that doesn't exist.
            }

            final String credentialId = storeCredential(hockeyappRecorder, existingCredentials, apiToken);

            hockeyappApplication.apiToken = null;
            hockeyappApplication.setCredentialId(credentialId);
        }
    }

    private String storeCredential(final HockeyappRecorder hockeyappRecorder, final List<StringCredentials> existingCredentials, final String apiToken) throws IOException {
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
        final BaseStandardCredentials credential = new StringCredentialsImpl(CredentialsScope.GLOBAL, id, id, Secret.fromString(apiToken));

        if (store.isDomainsModifiable()) {
            final String baseUrl = hockeyappRecorder.getBaseUrl();
            final Domain domain = store.getDomainByName(baseUrl);
            if (domain == null) {
                // If we don't have a domain in the store for our credential, create a domain and the credential at the same time.
                final List<DomainSpecification> specs = new ArrayList<>();
                specs.add(new HostnameSpecification(baseUrl, null));
                specs.add(new SchemeSpecification("https"));
                final Domain newDomain = new Domain(baseUrl, null, specs);
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

    private String generateCredentialId(List<String> existingIds) {
        // Users may already have a credential id with this name so try and account for this.
        String candidate = DEFAULT_TOKEN_NAME;
        int i = 2;
        while (existingIds.contains(candidate)) {
            candidate = DEFAULT_TOKEN_NAME + "-" + i++;
        }
        return candidate;
    }

    private List<DomainRequirement> requirements(String baseUrl) {
        return URIRequirementBuilder.fromUri(baseUrl).build();
    }
}
