package hockeyapp;

import hudson.*;
import hudson.model.*;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.RadioButtonSupportDescriptor;
import net.hockeyapp.jenkins.releaseNotes.ChangelogReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.FileReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.ManualReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.NoReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.tools.ant.types.FileSet;
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



public class HockeyappRecorder extends Recorder {

    public static final Long PLUGIN_VERSION_NUMBER = 1L;
    private static final String DEFAULT_HOCKEY_URL = "https://rink.hockeyapp.net";



    @Exported
    public Long pluginVersion;

    @Exported
    public String apiToken;
    @Exported
    public transient String appId;
    @Exported
    public boolean notifyTeam;
    @Exported
    public transient String buildNotes;
    @Exported
    public String filePath;
    @Exported
    public String dsymPath;
    @Exported
    public String tags;
    @Exported
    public boolean downloadAllowed;
    @Exported
    public transient boolean useChangelog;
    @Exported
    public transient boolean cleanupOld;
    @Exported
    public String numberOldVersions;
    @Exported
    public transient boolean useAppVersionURL;
    @Exported
    public boolean debugMode;
    @Exported
    public transient boolean useNotesTypeMarkdown;
    @Exported
    public transient String releaseNotesFileName;
    @Exported
    public RadioButtonSupport uploadMethod;
    @Exported
    public String baseUrl;

    @Exported
    RadioButtonSupport releaseNotesMethod;

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    @DataBoundConstructor
    public HockeyappRecorder(String apiToken, boolean notifyTeam,
                             String filePath, String dsymPath, String tags,
                             boolean downloadAllowed,
                             OldVersionHolder oldVersionHolder, boolean debugMode,
                             RadioButtonSupport uploadMethod, RadioButtonSupport releaseNotesMethod,
                             BaseUrlHolder baseUrlHolder) {

        this.apiToken = Util.fixEmptyAndTrim(apiToken);

        this.notifyTeam = notifyTeam;

        this.filePath = Util.fixEmptyAndTrim(filePath);
        this.dsymPath = Util.fixEmptyAndTrim(dsymPath);
        this.tags = Util.fixEmptyAndTrim(tags);
        this.downloadAllowed = downloadAllowed;



        this.debugMode = debugMode;


        this.uploadMethod = uploadMethod;
        this.releaseNotesMethod = releaseNotesMethod;
        this.pluginVersion = PLUGIN_VERSION_NUMBER;
        if (baseUrlHolder != null) {
            this.baseUrl = baseUrlHolder.baseUrl;
        }
        if (oldVersionHolder != null) {
            this.numberOldVersions = Util.fixEmptyAndTrim(oldVersionHolder.numberOldVersions);
        }
    }

    @Deprecated
    public HockeyappRecorder(String apiToken, String appId, boolean notifyTeam,
                             String buildNotes, String filePath, String dsymPath, String tags,
                             boolean downloadAllowed, boolean useChangelog, boolean cleanupOld,
                             OldVersionHolder oldVersionHolder, boolean useAppVersionURL, boolean debugMode,
                             boolean useNotesTypeMarkdown, String releaseNotesFileName, RadioButtonSupport uploadMethod, RadioButtonSupport releaseNotesMethod, BaseUrlHolder baseUrlHolder) {

        this.apiToken = Util.fixEmptyAndTrim(apiToken);
        this.appId = Util.fixEmptyAndTrim(appId);
        this.notifyTeam = notifyTeam;
        this.buildNotes = Util.fixEmptyAndTrim(buildNotes);
        this.filePath = Util.fixEmptyAndTrim(filePath);
        this.dsymPath = Util.fixEmptyAndTrim(dsymPath);
        this.tags = Util.fixEmptyAndTrim(tags);
        this.downloadAllowed = downloadAllowed;
        this.useChangelog = useChangelog;
        this.cleanupOld = cleanupOld;


        this.useAppVersionURL = useAppVersionURL;
        this.debugMode = debugMode;
        this.useNotesTypeMarkdown = useNotesTypeMarkdown;
        this.releaseNotesFileName = Util.fixEmptyAndTrim(releaseNotesFileName);
        this.uploadMethod = uploadMethod;
        this.releaseNotesMethod = releaseNotesMethod;
        this.pluginVersion = PLUGIN_VERSION_NUMBER;
        if (baseUrlHolder != null) {
            this.baseUrl = baseUrlHolder.baseUrl;
        }
        if (oldVersionHolder != null) {
            this.numberOldVersions = Util.fixEmptyAndTrim(oldVersionHolder.numberOldVersions);
        }
    }

//    @Deprecated
//    public HockeyappRecorder(String apiToken, String appId, boolean notifyTeam,
//                             String buildNotes, String filePath, String dsymPath, String tags,
//                             boolean downloadAllowed, boolean useChangelog, boolean cleanupOld,
//                             String numberOldVersions, boolean useAppVersionURL, boolean debugMode,
//                             boolean useNotesTypeMarkdown, String releaseNotesFileName) {
//
//        this.apiToken = Util.fixEmptyAndTrim(apiToken);
//        this.appId = Util.fixEmptyAndTrim(appId);
//        this.notifyTeam = notifyTeam;
//        this.buildNotes = Util.fixEmptyAndTrim(buildNotes);
//        this.filePath = Util.fixEmptyAndTrim(filePath);
//        this.dsymPath = Util.fixEmptyAndTrim(dsymPath);
//        this.tags = Util.fixEmptyAndTrim(tags);
//        this.downloadAllowed = downloadAllowed;
//        this.useChangelog = useChangelog;
//        this.cleanupOld = cleanupOld;
//        this.numberOldVersions = Util.fixEmptyAndTrim(numberOldVersions);
//        this.useAppVersionURL = useAppVersionURL;
//        this.debugMode = debugMode;
//        this.useNotesTypeMarkdown = useNotesTypeMarkdown;
//        this.releaseNotesFileName = Util.fixEmptyAndTrim(releaseNotesFileName);
//        //this.uploadMethod = uploadMethod;
//        //this.releaseNotesMethod = releaseNotesMethod;
//    }

    public RadioButtonSupport getUploadMethod() {
        return uploadMethod;
    }

    public RadioButtonSupport getReleaseNotesMethod() {
        return releaseNotesMethod;
    }

    public Object readResolve() {
        if (this.pluginVersion == null || this.pluginVersion < PLUGIN_VERSION_NUMBER) {
            if (useChangelog) {
                if (buildNotes != null) {
                    //  entity.addPart("notes", new StringBody(vars.expand(buildNotes), UTF8_CHARSET));
                    //  entity.addPart("notes_type", new StringBody(useNotesTypeMarkdown ? "1" : "0"));
                    this.releaseNotesMethod = new ManualReleaseNotes(buildNotes, useNotesTypeMarkdown);
                } else if (releaseNotesFileName != null) {
//                File releaseNotesFile = getFileLocally(build.getWorkspace(), vars.expand(releaseNotesFileName), tempDir);
//                listener.getLogger().println(releaseNotesFile);
//                String releaseNotes = readReleaseNotesFile(releaseNotesFile);
//                entity.addPart("notes", new StringBody(releaseNotes, UTF8_CHARSET));
//                entity.addPart("notes_type", new StringBody(useNotesTypeMarkdown ? "1" : "0"));
                    this.releaseNotesMethod = new FileReleaseNotes(releaseNotesFileName, useNotesTypeMarkdown);
                } else {
                    this.releaseNotesMethod = new ChangelogReleaseNotes();
                }
            }
            if (useAppVersionURL && (appId != null)) {
                this.uploadMethod = new VersionCreation(appId);
            } else {
                this.uploadMethod = new AppCreation();
            }


        }
        return this;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    // Not a getter since build has to know proper value
    public String fetchApiToken() {
        if (this.apiToken == null) {
            return getDescriptor().getDefaultToken();
        } else {
            return this.apiToken;
        }
    }

    public boolean isDebugEnabled() {
        return this.debugMode || this.getDescriptor().getGlobalDebugMode();
    }

    // create an httpclient with some default settings, including socket timeouts
    // note that this doesn't solve potential write timeouts
    // http://stackoverflow.com/questions/1338885/java-socket-output-stream-writes-do-they-block
    private HttpClient createPreconfiguredHttpClient() {
        HttpClient httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 60000);
        httpclient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 60000);
        return httpclient;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) {
        if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
            return false;

        listener.getLogger().println(Messages.UPLOADING_TO_HOCKEYAPP());
        File tempDir = null;
        try {
            EnvVars vars = build.getEnvironment(listener);

            // Copy remote file to local file system.
            tempDir = File.createTempFile("jtf", null);
            tempDir.delete();
            tempDir.mkdirs();

            FileSet fileSet = Util.createFileSet(new File(build.getWorkspace().getRemote()),
                    vars.expand(filePath), null);
            // Take the first one that matches the pattern
            File file = new File(fileSet.iterator().next().toString());
            listener.getLogger().println(file);

            float fileSize = file.length();

            if (uploadMethod == null) {
                listener.getLogger().println("No upload method specified!");
                return false;
            }

            String path = createPath(listener, vars);
            URL host = createHostUrl(vars);
            URL url = new URL(host, path);
            if (url == null) {
                return false;
            }

            HttpClient httpclient = createPreconfiguredHttpClient();

            HttpPost httpPost = new HttpPost(url.toURI());


            FileBody fileBody = new FileBody(file);
            httpPost.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken()));
            MultipartEntity entity = new MultipartEntity();

            if (releaseNotesMethod != null) {
                createReleaseNotes(build, entity, listener, tempDir, vars);

            }


            entity.addPart("ipa", fileBody);

            if (dsymPath != null) {
                FileSet dsymFileSet = Util.createFileSet(new File(build.getWorkspace().getRemote()),
                        vars.expand(dsymPath), null);
                // Take the first one that matches the pattern
                File dsymFile = new File(dsymFileSet.iterator().next().toString());
                listener.getLogger().println(dsymFile);
                FileBody dsymFileBody = new FileBody(dsymFile);
                entity.addPart("dsym", dsymFileBody);
            }

            if (tags != null && tags.length() > 0)
                entity.addPart("tags", new StringBody(vars.expand(tags)));
            entity.addPart("notify", new StringBody(notifyTeam ? "1" : "0"));
            entity.addPart("status",
                    new StringBody(downloadAllowed ? "2" : "1"));
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
                return false;
            } else if (isDebugEnabled()) { // DEBUG MODE output
                listener.getLogger().println("RESPONSE: " + responseBody);
            }

            JSONParser parser = new JSONParser();

            final Map parsedMap = (Map) parser.parse(responseBody);



            HockeyappBuildAction installAction = new HockeyappBuildAction();
            installAction.displayName = Messages.HOCKEYAPP_INSTALL_LINK();
            installAction.iconFileName = "package.gif";
            installAction.urlName = (String) parsedMap.get("public_url");
            build.addAction(installAction);

            HockeyappBuildAction configureAction = new HockeyappBuildAction();
            configureAction.displayName = Messages.HOCKEYAPP_CONFIG_LINK();
            configureAction.iconFileName = "gear2.gif";
            configureAction.urlName = (String) parsedMap.get("config_url");
            build.addAction(configureAction);

            String appId;
            if (numberOldVersions != null) {
                if (uploadMethod instanceof VersionCreation) {
                    appId = ((VersionCreation) uploadMethod).getAppId();
                } else {
                    //load App ID from reponse
                    appId = (String) parsedMap.get("public_identifier");
                }
                if (appId == null) {
                    listener.getLogger().println(Messages.APP_ID_MISSING_FOR_CLEANUP());
                    listener.getLogger().println(Messages.ABORTING_CLEANUP());
                    return false;
                }
                if (numberOldVersions == null || !StringUtils.isNumeric(numberOldVersions)) {
                    listener.getLogger().println(Messages.COUNT_MISSING_FOR_CLEANUP());
                    listener.getLogger().println(Messages.ABORTING_CLEANUP());
                    return false;
                }
                if (Integer.parseInt(numberOldVersions) < 1) {
                    listener.getLogger().println(Messages.TOO_FEW_VERSIONS_RETAINED());
                    listener.getLogger().println(Messages.ABORTING_CLEANUP());
                    return false;
                }
                cleanupOldVersions(listener, vars, appId, host);
            }
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            return false;
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

    private String createPath(BuildListener listener, EnvVars vars) {
        String path;
        if (uploadMethod instanceof VersionCreation) {
            VersionCreation versionCreation = (VersionCreation) uploadMethod;
            if (versionCreation.getAppId() != null) {
                path = "/api/2/apps/" + vars.expand(versionCreation.getAppId()) + "/app_versions";
            } else {
                listener.getLogger().println("No AppId specified!");
                path = null;
            }
        } else {
            path = "/api/2/apps/upload";
        }
        return path;
    }

    private void createReleaseNotes(AbstractBuild<?, ?> build, MultipartEntity entity, BuildListener listener, File tempDir, EnvVars vars) throws IOException, InterruptedException {
        if (releaseNotesMethod instanceof NoReleaseNotes) {
            return;
        } else if (releaseNotesMethod instanceof ManualReleaseNotes) {
            ManualReleaseNotes manualReleaseNotes = (ManualReleaseNotes) releaseNotesMethod;
            if (manualReleaseNotes.getReleaseNotes() != null) {
                entity.addPart("notes", new StringBody(vars.expand(manualReleaseNotes.getReleaseNotes()), UTF8_CHARSET));
                entity.addPart("notes_type", new StringBody(manualReleaseNotes.isMarkdown() ? "1" : "0"));
            }
        } else if (releaseNotesMethod instanceof FileReleaseNotes) {
            FileReleaseNotes fileReleaseNotes = (FileReleaseNotes) releaseNotesMethod;
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

    private boolean cleanupOldVersions(BuildListener listener, EnvVars vars, String appId, URL host) {
        try {
            HttpClient httpclient = createPreconfiguredHttpClient();
            String path = "/api/2/apps/" + vars.expand(appId)+ "/app_versions/delete";
            HttpPost httpPost = new HttpPost(new URL(host, path).toURI());
            httpPost.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken()));
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
            nameValuePairs.add(new BasicNameValuePair("keep", numberOldVersions));
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



    public static class OldVersionHolder {

        private String numberOldVersions;

        @DataBoundConstructor
        public OldVersionHolder(String numberOldVersions) {
            this.numberOldVersions = numberOldVersions;
        }

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
            this.defaultToken = defaultToken;
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

        public List<RadioButtonSupportDescriptor> getUploadMethodList() {
            List<RadioButtonSupportDescriptor> uploadMethods = new ArrayList<RadioButtonSupportDescriptor>(2);
            uploadMethods.add(Jenkins.getInstance() == null ? null : (RadioButtonSupportDescriptor) Jenkins.getInstance().getDescriptorOrDie(AppCreation.class));
            uploadMethods.add(Jenkins.getInstance() == null ? null : (RadioButtonSupportDescriptor) Jenkins.getInstance().getDescriptorOrDie(VersionCreation.class));
            return uploadMethods;
        }

        public List<RadioButtonSupportDescriptor> getReleaseNotesMethodList() {
            List<RadioButtonSupportDescriptor> uploadMethods = new ArrayList<RadioButtonSupportDescriptor>(3);
            uploadMethods.add(Jenkins.getInstance() == null ? null : (RadioButtonSupportDescriptor) Jenkins.getInstance().getDescriptorOrDie(NoReleaseNotes.class));
            uploadMethods.add(Jenkins.getInstance() == null ? null : (RadioButtonSupportDescriptor) Jenkins.getInstance().getDescriptorOrDie(ChangelogReleaseNotes.class));
            uploadMethods.add(Jenkins.getInstance() == null ? null : (RadioButtonSupportDescriptor) Jenkins.getInstance().getDescriptorOrDie(FileReleaseNotes.class));
            uploadMethods.add(Jenkins.getInstance() == null ? null : (RadioButtonSupportDescriptor) Jenkins.getInstance().getDescriptorOrDie(ManualReleaseNotes.class));
            return uploadMethods;
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckApiToken(@QueryParameter String value) throws IOException, ServletException {
            if(value.isEmpty()) {
                if (defaultToken != null && defaultToken.length() > 0) {
                    return FormValidation.warning("Default API Token is used.");
                } else {
                    return FormValidation.errorWithMarkup("You must enter an <a href=\"https://rink.hockeyapp.net/manage/auth_tokens\">API Token</a>.");
                }
            } else {
                return FormValidation.ok();
            }

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
        public FormValidation doCheckNumberOldVersions(@QueryParameter String value) throws IOException, ServletException {
            if(value.isEmpty()) {
                return FormValidation.error("You must specify a positive Number.");
            } else {
                try {
                    int number = Integer.parseInt(value);
                    if (number > 0) {
                        return FormValidation.ok();
                    } else {
                        return FormValidation.error("You must specify a positive Number.");
                    }
                } catch (NumberFormatException e) {
                    return FormValidation.error("You must specify a positive Number.");
                }

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

        @SuppressWarnings("unused")
        public FormValidation doCheckFilePath(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must enter a File Path.");
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


}
