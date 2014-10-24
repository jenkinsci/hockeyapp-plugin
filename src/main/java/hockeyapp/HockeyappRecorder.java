package hockeyapp;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import hudson.*;
import hudson.model.*;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.RunList;
import net.hockeyapp.jenkins.releaseNotes.FileReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.ManualReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.NoReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.simple.parser.JSONParser;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

@XStreamConverter(HockeyappRecorderConverter.class)
public class HockeyappRecorder extends Recorder {

    public static final long SCHEMA_VERSION_NUMBER = 2L;
    public static final String DEFAULT_HOCKEY_URL = "https://rink.hockeyapp.net";
    public static final int DEFAULT_TIMEOUT = 60000;

    @Exported
    @XStreamAsAttribute
    public long schemaVersion = SCHEMA_VERSION_NUMBER;

    @Exported
    public List<HockeyappApplication> applications;

    @Exported
    public boolean debugMode;
    @Exported
    public String baseUrl;

    @Exported
    public boolean failGracefully;

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    @DataBoundConstructor
    public HockeyappRecorder(List<HockeyappApplication> applications, boolean debugMode,
                             BaseUrlHolder baseUrlHolder, boolean failGracefully) {
        this.schemaVersion = SCHEMA_VERSION_NUMBER;
        this.applications = applications;
        this.debugMode = debugMode;
        if (baseUrlHolder != null) {
            this.baseUrl = baseUrlHolder.baseUrl;
        }

        this.failGracefully = failGracefully;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    // Not a getter since build has to know proper value
    public String fetchApiToken(HockeyappApplication application) {
        if (application.apiToken == null) {
            return getDescriptor().getDefaultToken();
        } else {
            return application.apiToken;
        }
    }

    public boolean isDebugEnabled() {
        return this.debugMode || this.getDescriptor().getGlobalDebugMode();
    }

    // create an httpclient with some default settings, including socket timeouts
    // note that this doesn't solve potential write timeouts
    // http://stackoverflow.com/questions/1338885/java-socket-output-stream-writes-do-they-block
    private HttpClient createPreconfiguredHttpClient() {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpParams params = httpclient.getParams();
        HttpConnectionParams.setConnectionTimeout(params, this.getDescriptor().getTimeoutInt());
        HttpConnectionParams.setSoTimeout(params, this.getDescriptor().getTimeoutInt());
        // Proxy setting
        if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {

            ProxyConfiguration configuration = Hudson.getInstance().proxy;
            Credentials cred = null;

            if (configuration.getUserName() != null && !configuration.getUserName().isEmpty()) {
                cred = new UsernamePasswordCredentials(configuration.getUserName(), configuration.getPassword());
            }

            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(configuration.name, configuration.port), cred);

            httpclient.getCredentialsProvider().setCredentials(new AuthScope(configuration.name, configuration.port), cred);
            HttpHost proxy = new HttpHost(configuration.name, configuration.port);
            httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        return httpclient;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) {
        if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
            return false;

        boolean result = true;
        for (HockeyappApplication application : applications) {
            result &= performForApplication(build, launcher, listener, application);
        }

        return result;
    }

    private boolean performForApplication(AbstractBuild<?, ?> build, Launcher launcher,
                                          BuildListener listener, HockeyappApplication application) {
        listener.getLogger().println(Messages.UPLOADING_TO_HOCKEYAPP());
        File tempDir = null;
        try {
            EnvVars vars = build.getEnvironment(listener);

            // Copy remote file to local file system.
            tempDir = File.createTempFile("jtf", null);
            tempDir.delete();
            tempDir.mkdirs();

            FilePath remoteWorkspace = new FilePath(launcher.getChannel(), build.getWorkspace().getRemote());
            FilePath[] remoteFiles = remoteWorkspace.list(vars.expand(application.filePath));
            File file = getLocalFileFromFilePath(remoteFiles[0], tempDir);
            listener.getLogger().println(file);

            float fileSize = file.length();

            if (application.uploadMethod == null) {
                listener.getLogger().println("No upload method specified!");
                return this.failGracefully;
            }

            String path = createPath(listener, vars, application);
            URL host = createHostUrl(vars);
            URL url = new URL(host, path);
            if (url == null) {
                return this.failGracefully;
            }

            HttpClient httpclient = createPreconfiguredHttpClient();

            HttpPost httpPost = new HttpPost(url.toURI());


            FileBody fileBody = new FileBody(file);
            httpPost.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken(application)));
            MultipartEntity entity = new MultipartEntity();

            if (application.releaseNotesMethod != null) {
                createReleaseNotes(build, entity, listener, tempDir, vars, application);

            }


            entity.addPart("ipa", fileBody);

            if (application.dsymPath != null) {
                FilePath remoteDsymFiles[] = remoteWorkspace.list(vars.expand(application.dsymPath));
                // Take the first one that matches the pattern
                if(remoteDsymFiles!=null && remoteDsymFiles.length>0) {
                    File dsymFile = getLocalFileFromFilePath(remoteDsymFiles[0], tempDir);
                    listener.getLogger().println(dsymFile);
                    FileBody dsymFileBody = new FileBody(dsymFile);
                    entity.addPart("dsym", dsymFileBody);
                }
            }

            if (application.libsPath != null) {
                FilePath remoteLibsFiles[] = remoteWorkspace.list(vars.expand(application.libsPath));
                // Take the first one that matches the pattern
                File libsFile = getLocalFileFromFilePath(remoteLibsFiles[0], tempDir);
                listener.getLogger().println(libsFile);
                FileBody libsFileBody = new FileBody(libsFile);
                entity.addPart("libs", libsFileBody);
            }

            if (application.tags != null && application.tags.length() > 0)
                entity.addPart("tags", new StringBody(vars.expand(application.tags)));
            entity.addPart("notify", new StringBody(application.notifyTeam ? "1" : "0"));
            entity.addPart("status",
                    new StringBody(application.downloadAllowed ? "2" : "1"));
            httpPost.setEntity(entity);

            long startTime = System.currentTimeMillis();
            HttpResponse response = httpclient.execute(httpPost);
            long duration = System.currentTimeMillis() - startTime;

            printUploadSpeed(duration, fileSize, listener);

            HttpEntity resEntity = response.getEntity();

            InputStream is = resEntity.getContent();

            String responseBody = IOUtils.toString(is);
            // Improved error handling.
            if (response.getStatusLine().getStatusCode() != 201) {
                listener.getLogger().println(
                        Messages.UNEXPECTED_RESPONSE_CODE(response.getStatusLine().getStatusCode()));
                listener.getLogger().println(responseBody);
                return this.failGracefully;
            } else if (isDebugEnabled()) { // DEBUG MODE output
                listener.getLogger().println("RESPONSE: " + responseBody);
            }

            JSONParser parser = new JSONParser();

            final Map parsedMap = (Map) parser.parse(responseBody);



            String buildId = Long.toString((Long)parsedMap.get("id"));

            HockeyappBuildAction installAction = new HockeyappBuildAction();
            String installUrl = (String) parsedMap.get("public_url") +
                "/app_versions/" + buildId;
            installAction.displayName = Messages.HOCKEYAPP_INSTALL_LINK();
            installAction.iconFileName = "package.gif";
            installAction.urlName = installUrl;
            build.addAction(installAction);

            HockeyappBuildAction configureAction = new HockeyappBuildAction();
            String configUrl = (String) parsedMap.get("config_url");
            configureAction.displayName = Messages.HOCKEYAPP_CONFIG_LINK();
            configureAction.iconFileName = "gear2.gif";
            configureAction.urlName = configUrl;
            build.addAction(configureAction);

            int appIndex = applications.indexOf(application);

            EnvAction envData = new EnvAction();
            build.addAction(envData);

            if (envData != null) {

                if (appIndex == 0) {
                    envData.add("HOCKEYAPP_INSTALL_URL", installUrl);
                    envData.add("HOCKEYAPP_CONFIG_URL", configUrl);
                }

                envData.add("HOCKEYAPP_INSTALL_URL_" + appIndex, installUrl);
                envData.add("HOCKEYAPP_CONFIG_URL_" + appIndex, configUrl);
            }

            String appId;
            if (application.getNumberOldVersions() != null) {
                if (application.uploadMethod instanceof VersionCreation) {
                    appId = ((VersionCreation) application.uploadMethod).getAppId();
                } else {
                    //load App ID from reponse
                    appId = (String) parsedMap.get("public_identifier");
                }
                if (appId == null) {
                    listener.getLogger().println(Messages.APP_ID_MISSING_FOR_CLEANUP());
                    listener.getLogger().println(Messages.ABORTING_CLEANUP());
                    return this.failGracefully;
                }
                if (application.getNumberOldVersions() == null || !StringUtils.isNumeric(application.getNumberOldVersions())) {
                    listener.getLogger().println(Messages.COUNT_MISSING_FOR_CLEANUP());
                    listener.getLogger().println(Messages.ABORTING_CLEANUP());
                    return this.failGracefully;
                }
                if (Integer.parseInt(application.getNumberOldVersions()) < 1) {
                    listener.getLogger().println(Messages.TOO_FEW_VERSIONS_RETAINED());
                    listener.getLogger().println(Messages.ABORTING_CLEANUP());
                    return this.failGracefully;
                }
                cleanupOldVersions(listener, vars, appId, host, application);
            }
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            return this.failGracefully;
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                try {
                    FileUtils.forceDeleteOnExit(tempDir);
                } catch (IOException e1) {
                    e1.printStackTrace(listener.getLogger());
                }
            }
        }

        return true;
    }

    private String createPath(BuildListener listener, EnvVars vars, HockeyappApplication application) {
        String path;
        if (application.uploadMethod instanceof VersionCreation) {
            VersionCreation versionCreation = (VersionCreation) application.uploadMethod;
            if (versionCreation.getAppId() != null) {
                path = "/api/2/apps/" + vars.expand(versionCreation.getAppId()) + "/app_versions/upload";
            } else {
                listener.getLogger().println("No AppId specified!");
                path = null;
            }
        } else {
            path = "/api/2/apps/upload";
        }
        return path;
    }

    private void createReleaseNotes(AbstractBuild<?, ?> build, MultipartEntity entity, BuildListener listener,
                                    File tempDir, EnvVars vars, HockeyappApplication application)
            throws IOException, InterruptedException {
        if (application.releaseNotesMethod instanceof NoReleaseNotes) {
            return;
        } else if (application.releaseNotesMethod instanceof ManualReleaseNotes) {
            ManualReleaseNotes manualReleaseNotes = (ManualReleaseNotes) application.releaseNotesMethod;
            if (manualReleaseNotes.getReleaseNotes() != null) {
                entity.addPart("notes", new StringBody(vars.expand(manualReleaseNotes.getReleaseNotes()), UTF8_CHARSET));
                entity.addPart("notes_type", new StringBody(manualReleaseNotes.isMarkdown() ? "1" : "0"));
            }
        } else if (application.releaseNotesMethod instanceof FileReleaseNotes) {
            FileReleaseNotes fileReleaseNotes = (FileReleaseNotes) application.releaseNotesMethod;
            if (fileReleaseNotes.getFileName() != null) {
                File releaseNotesFile = getFileLocally(build.getWorkspace(), vars.expand(fileReleaseNotes.getFileName()), tempDir);
                listener.getLogger().println(releaseNotesFile);
                String releaseNotes = readReleaseNotesFile(releaseNotesFile);
                entity.addPart("notes", new StringBody(releaseNotes, UTF8_CHARSET));
                entity.addPart("notes_type", new StringBody(fileReleaseNotes.isMarkdown() ? "1" : "0"));
            }

        } else {
            StringBuilder sb = new StringBuilder();
            if (!build.getChangeSet().isEmptySet()) {
                boolean hasManyChangeSets = build.getChangeSet().getItems().length > 1;
                for (Entry entry : build.getChangeSet()) {
                    sb.append("\n");
                    if (hasManyChangeSets) {
                        sb.append("* ");
                    }
                    sb.append(entry.getAuthor()).append(": ").append(entry.getMsg());
                }
            }
            entity.addPart("notes", new StringBody(sb.toString(), UTF8_CHARSET));
            entity.addPart("notes_type", new StringBody("0"));
        }

    }

    private URL createHostUrl(EnvVars vars) throws MalformedURLException {
        URL host;
        if (baseUrl != null) {
            host = new URL(vars.expand(baseUrl));
        } else {
            host = new URL(DEFAULT_HOCKEY_URL);
        }
        return host;
    }

    private void printUploadSpeed(long duration, float fileSize, BuildListener listener) {
        Float speed = fileSize / duration;
        speed *= 8000; // In order to get bits pers second not bytes per miliseconds

        if (Float.isNaN(speed)) listener.getLogger().println("NaN bps");

        String[] units = {"bps", "Kbps", "Mbps", "Gbps"};
        int idx = 0;
        while (speed > 1024 && idx <= units.length - 1) {
            speed /= 1024;
            idx += 1;
        }
        listener.getLogger().println("HockeyApp Upload Speed: " + String.format("%.2f", speed) + units[idx]);
    }


    private static File getFileLocally(FilePath workingDir, String strFile,
                                       File tempDir) throws IOException, InterruptedException {
        // Due to the previous inconsistency about whether or not to use absolute paths,
        // here we automatically remove the workspace, so that 'strFile' is relative
        // and existing jobs continue to function, regardless of how they were configured
        if (strFile.startsWith(workingDir.getRemote())) {
            strFile = strFile.substring(workingDir.getRemote().length() + 1);
        }

        if (workingDir.isRemote()) {
            FilePath remoteFile = new FilePath(workingDir, strFile);
            File file = new File(tempDir, remoteFile.getName());
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            remoteFile.copyTo(fos);
            fos.close();
            return file;
        }

        return new File(workingDir.getRemote(), strFile);
    }

    private static File getLocalFileFromFilePath(FilePath filePath, File tempDir) throws IOException, InterruptedException {
        if(filePath.isRemote()) {
            FilePath localFilePath = new FilePath(new FilePath(tempDir), filePath.getName());
            filePath.copyTo(localFilePath);
            return new File(localFilePath.toURI());
        } else {
            return new File(filePath.toURI());
        }
    }

    @Override
    public Collection<? extends Action> getProjectActions(
            AbstractProject<?, ?> project) {
        ArrayList<HockeyappBuildAction> actions = new ArrayList<HockeyappBuildAction>();
        RunList<? extends AbstractBuild<?, ?>> builds = project.getBuilds();

        @SuppressWarnings("unchecked")
        Collection<AbstractBuild<?, ?>> predicated = CollectionUtils.select(builds, new Predicate() {
            public boolean evaluate(Object o) {
                Result r = ((AbstractBuild<?, ?>) o).getResult();
                return r == null // no result yet
                        ? false
                        : r.isBetterOrEqualTo(Result.SUCCESS);
            }
        });

        ArrayList<AbstractBuild<?, ?>> filteredList = new ArrayList<AbstractBuild<?, ?>>(
                predicated);

        Collections.reverse(filteredList);
        for (AbstractBuild<?, ?> build : filteredList) {
            List<HockeyappBuildAction> hockeyappActions = build
                    .getActions(HockeyappBuildAction.class);
            if (hockeyappActions != null && hockeyappActions.size() > 0) {
                for (HockeyappBuildAction action : hockeyappActions) {
                    actions.add(new HockeyappBuildAction(action));
                }
                break;
            }
        }

        return actions;
    }

    private boolean cleanupOldVersions(BuildListener listener, EnvVars vars, String appId, URL host,
                                       HockeyappApplication application) {
        try {
            HttpClient httpclient = createPreconfiguredHttpClient();
            String path = "/api/2/apps/" + vars.expand(appId)+ "/app_versions/delete";
            HttpPost httpPost = new HttpPost(new URL(host, path).toURI());
            httpPost.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken(application)));
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
            nameValuePairs.add(new BasicNameValuePair("keep", application.getNumberOldVersions()));
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            HttpResponse response = httpclient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            if (resEntity != null) {
                InputStream is = resEntity.getContent();

                // Improved error handling.
                if (response.getStatusLine().getStatusCode() != 200) {
                    String responseBody = new Scanner(is).useDelimiter(
                            "\\A").next();
                    listener.getLogger().println(
                            Messages.UNEXPECTED_RESPONSE_CODE(
                                    response.getStatusLine().getStatusCode())
                    );
                    listener.getLogger().println(responseBody);
                    return false;
                }

                JSONParser parser = new JSONParser();
                final Map parsedMap = (Map) parser.parse(
                        new BufferedReader(new InputStreamReader(is)));
                listener.getLogger().println(
                        Messages.DELETED_OLD_VERSIONS(String.valueOf(
                                parsedMap.get("total_entries")))
                );
            }
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            return false;
        }
        return true;
    }

    public static class BaseUrlHolder {

        private String baseUrl;

        @DataBoundConstructor
        public BaseUrlHolder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

    }

    @Extension
    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super(HockeyappRecorder.class);
            load();
        }

        public String getDefaultToken() {
            return defaultToken;
        }

        @SuppressWarnings("unused") // Used by Jenkins
        public void setDefaultToken(String defaultToken) {
            this.defaultToken = Util.fixEmptyAndTrim(defaultToken);
            save();
        }

        public boolean getGlobalDebugMode() {
            return this.globalDebugMode;

        }

        @SuppressWarnings("unused") // Used by Jenkins
        public void setGlobalDebugMode(boolean globalDebugMode) {
            this.globalDebugMode = globalDebugMode;
            save();
        }

        private String defaultToken;

        private boolean globalDebugMode = false;

        private String timeout;

        @SuppressWarnings("unused")
        public String getTimeout() {
            return timeout;
        }

        public int getTimeoutInt() {
            if (this.timeout != null) {
                try {
                    return Integer.parseInt(this.timeout) * 1000;
                } catch (Exception e) {
                    return HockeyappRecorder.DEFAULT_TIMEOUT;
                }
            } else {
                return HockeyappRecorder.DEFAULT_TIMEOUT;
            }
        }

        @SuppressWarnings("unused")
        public void setTimeout(String timeout) {
            this.timeout = Util.fixEmptyAndTrim(timeout);
            save();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json)
                throws FormException {
            // XXX is this now the right style?
            req.bindJSON(this, json);
            save();
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.UPLOAD_TO_HOCKEYAPP();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckBaseUrl(@QueryParameter String value) throws IOException, ServletException {
            if(value.isEmpty()) {
                return FormValidation.error("You must enter a URL.");
            } else {
                return FormValidation.ok();
            }

        }

        @SuppressWarnings("unused")
        public FormValidation doCheckDebugMode(@QueryParameter String value) {
            if (globalDebugMode) {
                return FormValidation.warning("Debug Mode is enabled globally.");
            } else {
                return FormValidation.ok();
            }
        }

    }
    private String readReleaseNotesFile(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        try {
            return IOUtils.toString(inputStream, "UTF-8");
        } finally {
            inputStream.close();
        }
    }

    private static class EnvAction implements EnvironmentContributingAction {
        private transient Map<String, String> data = new HashMap<String, String>();

        private void add(String key, String value) {
            if (data == null) return;
            data.put(key, value);
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            if (data != null) env.putAll(data);
        }

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return null;
        }
    }


}
