package hockeyapp;

import hudson.*;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.hockeyapp.jenkins.releaseNotes.FileReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.ManualReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.iterators.ArrayIterator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
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
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class HockeyappRecorder extends Recorder implements SimpleBuildStep {

    public static final long SCHEMA_VERSION_NUMBER = 2L;
    public static final String DEFAULT_HOCKEY_URL = "https://rink.hockeyapp.net";
    public static final int DEFAULT_TIMEOUT = 60000;

    @Exported
    public final List<HockeyappApplication> applications;

    @Exported
    public boolean debugMode;
    @Exported
    public String baseUrl = DEFAULT_HOCKEY_URL;

    @Exported
    public boolean failGracefully;

    public BaseUrlHolder baseUrlHolder;

    private static final String UTF8 = "UTF-8";
    private static final Charset DEFAULT_CHARACTER_SET = StandardCharsets.UTF_8;
    private static final ContentType DEFAULT_CONTENT_TYPE = ContentType.create("text/plain", Consts.UTF_8);

    @Deprecated
    public HockeyappRecorder(@CheckForNull List<HockeyappApplication> applications, boolean debugMode,
                             @CheckForNull BaseUrlHolder baseUrlHolder, boolean failGracefully) {
        this.applications = Util.fixNull(applications);
        this.debugMode = debugMode;
        this.baseUrlHolder = baseUrlHolder;
        if (baseUrlHolder != null) {
            this.baseUrl = baseUrlHolder.baseUrl;
        }
        this.failGracefully = failGracefully;
    }

    @DataBoundConstructor
    public HockeyappRecorder(@CheckForNull List<HockeyappApplication> applications) {
        this.applications = Util.fixNull(applications);
    }

    List<HockeyappApplication> getApplications() {
        return applications;
    }

    boolean getDebugMode() {
        return debugMode;
    }

    String getBaseUrl() {
        return baseUrl;
    }

    BaseUrlHolder getBaseUrlHolder() {
        return baseUrlHolder;
    }

    boolean getFailGracefully() {
        return failGracefully;
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
    private HttpClient createPreconfiguredHttpClient(URL url, PrintStream logger) {
        final Jenkins instance = Jenkins.getInstance();

        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpParams params = httpclient.getParams();
        HttpConnectionParams.setConnectionTimeout(params, this.getDescriptor().getTimeoutInt());
        HttpConnectionParams.setSoTimeout(params, this.getDescriptor().getTimeoutInt());

        boolean hasProxy = instance.proxy != null;

        // ProxyConfig might have no proxy exception for certain hosts
        boolean useProxy = true;
        String matchedPattern = null; // to log properly
        if (hasProxy) {
            List<Pattern> noProxyHostPatterns = instance.proxy.getNoProxyHostPatterns();
            for (Pattern noProxyPattern : noProxyHostPatterns) {
                if (noProxyPattern.matcher(url.getHost()).matches()) {
                    useProxy = false;
                    matchedPattern = noProxyPattern.toString();
                }
            }
        }

        // Proxy setting, we have a Proxy _and_ the provided URL does not match any no-proxy-override
        if (hasProxy && useProxy) {
            ProxyConfiguration configuration = instance.proxy;

            if (configuration.getUserName() != null && !configuration.getUserName().isEmpty()
                    && configuration.getPassword() != null && !configuration.getPassword().isEmpty()) {
                Credentials credentials = new UsernamePasswordCredentials(configuration.getUserName(), configuration.getPassword());
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(configuration.name, configuration.port), credentials);
                httpclient.getCredentialsProvider().setCredentials(new AuthScope(configuration.name, configuration.port), credentials);
            }

            HttpHost proxy = new HttpHost(configuration.name, configuration.port);
            httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        // Logging output
        logger.format("Proxy Settings: For the URL [%s] %n", url)
                .format("  Found proxy configuration [%s] %n", hasProxy);
        if (hasProxy) {
            logger.format("  Used proxy configuration  [%s] %n", useProxy);
            if (matchedPattern != null) {
                logger.format("  Found matching Proxy exception rule [%s] %n", matchedPattern);
            }
        }

        return httpclient;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        final Result buildResult = build.getResult();
        if (buildResult != null && buildResult.isWorseOrEqualTo(Result.FAILURE)) {
            build.setResult(Result.FAILURE);
            return;
        }


        boolean result = true;
        for (HockeyappApplication application : applications) {
            result &= performForApplication(build, filePath, build.getEnvironment(listener), launcher, listener.getLogger(), application);
        }
        if (!result) {
            build.setResult(Result.FAILURE);
        }
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) {
        final Result buildResult = build.getResult();
        if (buildResult != null && buildResult.isWorseOrEqualTo(Result.FAILURE)) {
            return false;
        }

        boolean result = true;
        for (HockeyappApplication application : applications) {
            final FilePath workspace = build.getWorkspace();

            if (workspace != null) {
                try {
                    result &= performForApplication(build, workspace, build.getEnvironment(listener), launcher, listener.getLogger(), application);
                } catch (Exception e) {
                    e.printStackTrace(listener.getLogger());
                    return false;
                }
            } else {
                return false;
            }
        }
        return result;
    }

    private boolean performForApplication(Run<?, ?> build, FilePath workspace, EnvVars vars, Launcher launcher, PrintStream logger, HockeyappApplication application) {

        logger.println(Messages.UPLOADING_TO_HOCKEYAPP());
        File tempDir = null;
        try {

            // Copy remote file to local file system.
            tempDir = File.createTempFile("jtf", null);
            if (tempDir.delete() && tempDir.mkdirs()) {
                FilePath remoteWorkspace = new FilePath(launcher.getChannel(), workspace.getRemote());
                FilePath[] remoteFiles = remoteWorkspace.list(vars.expand(application.filePath));
                if (remoteFiles.length == 0) {
                    logger.println("No IPA/APK found to upload in: " + vars.expand(application.filePath));
                    return this.failGracefully;
                }

                ArrayIterator remoteFilesIterator = new ArrayIterator(remoteFiles);
                while (remoteFilesIterator.hasNext()) {
                    FilePath remoteFile = (FilePath) remoteFilesIterator.next();
                    File file = getLocalFileFromFilePath(remoteFile, tempDir);
                    logger.println(file);

                    float fileSize = file.length();

                    if (application.uploadMethod == null) {
                        logger.println("No upload method specified!");
                        return this.failGracefully;
                    }

                    HttpInfo info = getHttpInfo(logger, vars, application);
                    String path = info.getPath();
                    URL host = createHostUrl(vars);
                    URL url = new URL(host, path);

                    HttpClient httpclient = createPreconfiguredHttpClient(url, logger);

                    HttpEntityEnclosingRequestBase httpRequest = info.getMethod().equals(HttpPut.METHOD_NAME)
                            ? new HttpPut(url.toURI())
                            : new HttpPost(url.toURI());

                    FileBody fileBody = new FileBody(file);
                    httpRequest.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken(application)));
                    MultipartEntity entity = new MultipartEntity();

                    if (application.releaseNotesMethod != null) {
                        createReleaseNotes(build, workspace, entity, logger, tempDir, vars, application);
                    }


                    entity.addPart("ipa", fileBody);

                    if (application.dsymPath != null && !vars.expand(application.dsymPath).isEmpty()) {
                        FilePath remoteDsymFiles[] = remoteWorkspace.list(vars.expand(application.dsymPath));
                        // Take the first one that matches the pattern
                        if (remoteDsymFiles.length == 0) {
                            logger.println("No dSYM found to upload in: " + vars.expand(application.dsymPath));
                            return this.failGracefully;
                        }
                        File dsymFile = getLocalFileFromFilePath(remoteDsymFiles[0], tempDir);
                        logger.println(dsymFile);
                        FileBody dsymFileBody = new FileBody(dsymFile);
                        entity.addPart("dsym", dsymFileBody);
                    }

                    if (application.libsPath != null && !vars.expand(application.libsPath).isEmpty()) {
                        FilePath remoteLibsFiles[] = remoteWorkspace.list(vars.expand(application.libsPath));
                        // Take the first one that matches the pattern
                        if (remoteLibsFiles.length == 0) {
                            logger.println("No LIBS found to upload in: " + vars.expand(application.libsPath));
                            return this.failGracefully;
                        }
                        File libsFile = getLocalFileFromFilePath(remoteLibsFiles[0], tempDir);
                        logger.println(libsFile);
                        FileBody libsFileBody = new FileBody(libsFile);
                        entity.addPart("libs", libsFileBody);
                    }

                    if (application.tags != null && !vars.expand(application.tags).isEmpty() && application.tags.length() > 0)
                        entity.addPart("tags", new StringBody(vars.expand(application.tags), DEFAULT_CONTENT_TYPE));

                    entity.addPart("mandatory", new StringBody(application.mandatory ? "1" : "0", DEFAULT_CONTENT_TYPE));

                    if (application.teams != null && !vars.expand(application.teams).isEmpty() && application.teams.length() > 0)
                        entity.addPart("teams", new StringBody(vars.expand(application.teams), DEFAULT_CONTENT_TYPE));

                    entity.addPart("notify", new StringBody(application.notifyTeam ? "1" : "0", DEFAULT_CONTENT_TYPE));
                    entity.addPart("status", new StringBody(application.downloadAllowed ? "2" : "1", DEFAULT_CONTENT_TYPE));
                    if (application.uploadMethod instanceof AppCreation) {
                        AppCreation appCreation = (AppCreation) application.uploadMethod;
                        entity.addPart("private", new StringBody(appCreation.publicPage ? "false" : "true", DEFAULT_CONTENT_TYPE));
                    }
                    httpRequest.setEntity(entity);

                    long startTime = System.currentTimeMillis();
                    HttpResponse response = httpclient.execute(httpRequest);
                    long duration = System.currentTimeMillis() - startTime;

                    printUploadSpeed(duration, fileSize, logger);

                    HttpEntity resEntity = response.getEntity();

                    InputStream is = resEntity.getContent();

                    String responseBody = IOUtils.toString(is, DEFAULT_CHARACTER_SET);
                    // Improved error handling.
                    if (response.getStatusLine().getStatusCode() != 201) {
                        logger.println(
                                Messages.UNEXPECTED_RESPONSE_CODE(response.getStatusLine().getStatusCode()));
                        logger.println(responseBody);
                        return this.failGracefully;
                    } else if (isDebugEnabled()) { // DEBUG MODE output
                        logger.println("RESPONSE: " + responseBody);
                    }

                    JSONParser parser = new JSONParser();

                    final Map parsedMap = (Map) parser.parse(responseBody);


                    String buildId = Long.toString((Long) parsedMap.get("id"));

                    HockeyappBuildAction installAction = new HockeyappBuildAction();
                    EnvAction envData = new EnvAction();
                    int appIndex = applications.indexOf(application);

                    HockeyappBuildAction configureAction = new HockeyappBuildAction();
                    String configUrl = (String) parsedMap.get("config_url");
                    configureAction.displayName = Messages.HOCKEYAPP_CONFIG_LINK();
                    configureAction.iconFileName = "gear2.gif";
                    configureAction.urlName = configUrl;
                    build.addAction(configureAction);

                    if (appIndex == 0) {
                        envData.add("HOCKEYAPP_CONFIG_URL", configUrl);
                        logger.println("HOCKEYAPP_CONFIG_URL: " + configUrl);
                    }

                    envData.add("HOCKEYAPP_CONFIG_URL_" + appIndex, configUrl);
                    logger.println("HOCKEYAPP_CONFIG_URL_" + appIndex + ": " + configUrl);

                    String publicUrl = (String) parsedMap.get("public_url");
                    if (publicUrl != null) {
                        final String appVersion = configUrl.substring(configUrl.indexOf("/app_versions/"));
                        String installUrl = publicUrl + appVersion;
                        installAction.displayName = Messages.HOCKEYAPP_INSTALL_LINK();
                        installAction.iconFileName = "package.gif";
                        installAction.urlName = installUrl;
                        build.addAction(installAction);

                        if (appIndex == 0) {
                            envData.add("HOCKEYAPP_INSTALL_URL", installUrl);
                            logger.println("HOCKEYAPP_INSTALL_URL: " + installUrl);
                        }

                        envData.add("HOCKEYAPP_INSTALL_URL_" + appIndex, installUrl);
                        logger.println("HOCKEYAPP_INSTALL_URL_" + appIndex + ": " + installUrl);
                    }

                    build.addAction(envData);

                    String appId;
                    if (application.getNumberOldVersions() != null) {
                        if (application.uploadMethod instanceof VersionCreation) {
                            appId = vars.expand(((VersionCreation) application.uploadMethod).getAppId());
                        } else {
                            //load App ID from response
                            appId = (String) parsedMap.get("public_identifier");
                        }
                        if (appId == null) {
                            logger.println(Messages.APP_ID_MISSING_FOR_CLEANUP());
                            logger.println(Messages.ABORTING_CLEANUP());
                            return this.failGracefully;
                        }
                        if (application.getNumberOldVersions() == null || !StringUtils.isNumeric(application.getNumberOldVersions())) {
                            logger.println(Messages.COUNT_MISSING_FOR_CLEANUP());
                            logger.println(Messages.ABORTING_CLEANUP());
                            return this.failGracefully;
                        }
                        if (Integer.parseInt(application.getNumberOldVersions()) < 1) {
                            logger.println(Messages.TOO_FEW_VERSIONS_RETAINED());
                            logger.println(Messages.ABORTING_CLEANUP());
                            return this.failGracefully;
                        }
                        cleanupOldVersions(logger, vars, appId, host, application);
                    }
                }
            }
        } catch (IOException | URISyntaxException | InterruptedException | ParseException e) {
            e.printStackTrace(logger);
            return this.failGracefully;
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                try {
                    FileUtils.forceDeleteOnExit(tempDir);
                } catch (IOException e1) {
                    e1.printStackTrace(logger);
                }
            }
        }

        return true;

    }

    private HttpInfo getHttpInfo(PrintStream logger, EnvVars vars, HockeyappApplication application) {
        HttpInfo info = new HttpInfo();
        if (application.uploadMethod instanceof VersionCreation) {
            VersionCreation versionCreation = (VersionCreation) application.uploadMethod;
            if (!vars.expand(versionCreation.getAppId()).isEmpty()) {
                info.setPath("/api/2/apps/" + vars.expand(versionCreation.getAppId()) + "/app_versions/");
                if (versionCreation.getVersionCode() != null && !vars.expand(versionCreation.getVersionCode()).isEmpty()) {
                    // Update an existing version
                    // https://support.hockeyapp.net/kb/api/api-versions#update-version
                    info.setPath(info.getPath() + vars.expand(versionCreation.getVersionCode()));
                    info.setMethod(HttpPut.METHOD_NAME);
                } else {
                    // Upload a new version
                    // https://support.hockeyapp.net/kb/api/api-versions#upload-version
                    info.setPath(info.getPath() + "upload");
                }
            } else {
                logger.println("No AppId specified!");
                info.setPath(null);
            }
        } else {
            // Uploads app and assigns a new version. Only if there is no existing app?
            // https://support.hockeyapp.net/kb/api/api-apps#upload-app
            info.setPath("/api/2/apps/upload");
        }
        return info;
    }

    private void createReleaseNotes(Run<?, ?> build, FilePath workspace, MultipartEntity entity, PrintStream logger,
                                    File tempDir, EnvVars vars, HockeyappApplication application)
            throws IOException, InterruptedException {
        if (application.releaseNotesMethod instanceof ManualReleaseNotes) {
            ManualReleaseNotes manualReleaseNotes = (ManualReleaseNotes) application.releaseNotesMethod;
            if (manualReleaseNotes.getReleaseNotes() != null) {
                entity.addPart("notes", new StringBody(vars.expand(manualReleaseNotes.getReleaseNotes()), DEFAULT_CONTENT_TYPE));
                entity.addPart("notes_type", new StringBody(manualReleaseNotes.isMarkdown() ? "1" : "0", DEFAULT_CONTENT_TYPE));
            }
        } else if (application.releaseNotesMethod instanceof FileReleaseNotes) {
            FileReleaseNotes fileReleaseNotes = (FileReleaseNotes) application.releaseNotesMethod;
            if (fileReleaseNotes.getFileName() != null) {
                File releaseNotesFile = getFileLocally(workspace, vars.expand(fileReleaseNotes.getFileName()), tempDir);
                logger.println(releaseNotesFile);
                String releaseNotes = readReleaseNotesFile(releaseNotesFile);
                entity.addPart("notes", new StringBody(releaseNotes, DEFAULT_CONTENT_TYPE));
                entity.addPart("notes_type", new StringBody(fileReleaseNotes.isMarkdown() ? "1" : "0", DEFAULT_CONTENT_TYPE));
            }
        } else {
            StringBuilder sb = new StringBuilder();

            ChangeLogSet<? extends Entry> changeLogSet;
            if (build instanceof AbstractBuild) {
                changeLogSet = ((AbstractBuild) build).getChangeSet();
            } else if (build instanceof WorkflowRun) {
                // To support multi branch pipelines
                List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeLogSetList = ((WorkflowRun) build).getChangeSets();
                changeLogSet = changeLogSetList.isEmpty() ? null : changeLogSetList.get(0);
            } else {
                changeLogSet = getChangeLogSetFromRun(build);
            }
            if (changeLogSet != null && !changeLogSet.isEmptySet()) {
                boolean hasManyChangeSets = changeLogSet.getItems().length > 1;
                for (Entry entry : changeLogSet) {
                    sb.append("\n");
                    if (hasManyChangeSets) {
                        sb.append("* ");
                    }
                    sb.append(entry.getAuthor()).append(": ").append(entry.getMsg());
                }
            }

            entity.addPart("notes", new StringBody(sb.toString(), DEFAULT_CONTENT_TYPE));
            entity.addPart("notes_type", new StringBody("0", DEFAULT_CONTENT_TYPE));
        }

    }

    private ChangeLogSet<? extends Entry> getChangeLogSetFromRun(Run<?, ?> build) {
        ItemGroup<?> ig = build.getParent().getParent();
        for (Item item : ig.getItems()) {
            if (!item.getFullDisplayName().equals(build.getFullDisplayName())
                    && !item.getFullDisplayName().equals(build.getParent().getFullDisplayName())) {
                continue;
            }

            for (Job<?, ?> job : item.getAllJobs()) {
                if (job instanceof AbstractProject<?, ?>) {
                    //log("Job: " + job.getFullName());
                    AbstractProject<?, ?> p = (AbstractProject<?, ?>) job;
                    //log("Project: " + p.getFullName());
                    return p.getBuilds().getLastBuild().getChangeSet();
                }
            }
        }
        return null;
    }

    private URL createHostUrl(EnvVars vars) throws MalformedURLException {
        return new URL(vars.expand(baseUrl));
    }

    private void printUploadSpeed(long duration, float fileSize, PrintStream logger) {
        float speed = fileSize / duration;
        speed *= 8000; // In order to get bits per second not bytes per milliseconds

        if (Float.isNaN(speed)) logger.println("NaN bps");

        String[] units = {"bps", "Kbps", "Mbps", "Gbps"};
        int idx = 0;
        while (speed > 1024 && idx <= units.length - 1) {
            speed /= 1024;
            idx += 1;
        }
        logger.println("HockeyApp Upload Speed: " + String.format("%.2f", speed) + units[idx]);
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
            if (file.createNewFile()) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    remoteFile.copyTo(fos);
                }
                return file;
            }
        }

        return new File(workingDir.getRemote(), strFile);
    }

    private static File getLocalFileFromFilePath(FilePath filePath, File tempDir) throws IOException, InterruptedException {
        if (filePath.isRemote()) {
            FilePath localFilePath = new FilePath(new FilePath(tempDir), filePath.getName());
            filePath.copyTo(localFilePath);
            return new File(localFilePath.toURI());
        } else {
            return new File(filePath.toURI());
        }
    }

    @Nonnull
    @Override
    public Collection<? extends Action> getProjectActions(
            AbstractProject<?, ?> project) {
        ArrayList<HockeyappBuildAction> actions = new ArrayList<>();
        RunList<? extends AbstractBuild<?, ?>> builds = project.getBuilds();

        @SuppressWarnings("unchecked")
        Collection<AbstractBuild<?, ?>> predicated = CollectionUtils.select(builds, object -> {
            Result r = ((AbstractBuild<?, ?>) object).getResult();
            // no result yet
            return r != null && r.isBetterOrEqualTo(Result.SUCCESS);
        });

        ArrayList<AbstractBuild<?, ?>> filteredList = new ArrayList<>(
                predicated);

        Collections.reverse(filteredList);
        for (AbstractBuild<?, ?> build : filteredList) {
            List<HockeyappBuildAction> hockeyappActions = build
                    .getActions(HockeyappBuildAction.class);
            if (hockeyappActions.size() > 0) {
                for (HockeyappBuildAction action : hockeyappActions) {
                    actions.add(new HockeyappBuildAction(action));
                }
                break;
            }
        }

        return actions;
    }

    private void cleanupOldVersions(PrintStream logger, EnvVars vars, String appId, URL host,
                                    HockeyappApplication application) {
        try {
            String path = "/api/2/apps/" + vars.expand(appId) + "/app_versions/delete";
            URL url = new URL(host, path);
            HttpClient httpclient = createPreconfiguredHttpClient(url, logger);
            HttpPost httpPost = new HttpPost(url.toURI());
            httpPost.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken(application)));
            List<NameValuePair> nameValuePairs = new ArrayList<>(1);
            nameValuePairs.add(new BasicNameValuePair("keep", application.getNumberOldVersions()));
            nameValuePairs.add(new BasicNameValuePair("sort", application.getSortOldVersions()));
            nameValuePairs.add(new BasicNameValuePair("strategy", application.getStrategyOldVersions()));
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            HttpResponse response = httpclient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            if (resEntity != null) {
                InputStream is = resEntity.getContent();

                // Improved error handling.
                if (response.getStatusLine().getStatusCode() != 200) {
                    String responseBody = new Scanner(is, UTF8).useDelimiter(
                            "\\A").next();
                    logger.println(
                            Messages.UNEXPECTED_RESPONSE_CODE(
                                    response.getStatusLine().getStatusCode())
                    );
                    logger.println(responseBody);
                    return;
                }

                JSONParser parser = new JSONParser();
                final Map parsedMap = (Map) parser.parse(
                        new BufferedReader(new InputStreamReader(is, DEFAULT_CHARACTER_SET)));
                logger.println(
                        Messages.DELETED_OLD_VERSIONS(String.valueOf(
                                parsedMap.get("total_entries")))
                );
            }
        } catch (Exception e) {
            e.printStackTrace(logger);
        }
    }

    public static class BaseUrlHolder {

        private final String baseUrl;

        @DataBoundConstructor
        public BaseUrlHolder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }

    @Symbol("hockeyApp")
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
        public boolean configure(StaplerRequest req, JSONObject json) {
            // XXX is this now the right style?
            req.bindJSON(this, json);
            save();
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.UPLOAD_TO_HOCKEYAPP();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckTimeout(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            } else if (!StringUtils.isNumeric(value)) {
                return FormValidation.error("Must be an integer value.");
            } else {
                return FormValidation.ok();
            }
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckBaseUrl(@QueryParameter String value) {
            if (value.isEmpty()) {
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
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return IOUtils.toString(inputStream, "UTF-8");
        }
    }

    private static class EnvAction implements EnvironmentContributingAction {
        private final transient Map<String, String> data = new HashMap<>();

        private void add(String key, String value) {
            if (data == null) return;
            data.put(key, value);
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            if (data != null) env.putAll(data);
        }

        public void buildEnvironment(@Nonnull Run<?, ?> build, @Nonnull EnvVars env) {
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
