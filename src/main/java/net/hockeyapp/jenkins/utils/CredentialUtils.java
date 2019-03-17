package net.hockeyapp.jenkins.utils;

/**
 * Utilities to help with credential related tasks, such as credential lookup and migration of insecurely
 * stored credentials.
 * <p>
 * Based on the same class from https://github.com/jenkinsci/hipchat-plugin
 */
public class CredentialUtils {

    private static CredentialUtils instance;

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
}
