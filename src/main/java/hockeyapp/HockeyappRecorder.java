package hockeyapp;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.model.AbstractBuild;
import hudson.tasks.*;
import hudson.util.RunList;
import org.apache.commons.collections.Predicate;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.tools.ant.types.FileSet;
import org.json.simple.parser.JSONParser;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.scm.ChangeLogSet.Entry;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;



public class HockeyappRecorder extends Recorder {

	@Exported public String apiToken;
	@Exported public String appId;
	@Exported public boolean notifyTeam;
	@Exported public String buildNotes;
	@Exported public String filePath;
	@Exported public String dsymPath;
	@Exported public String tags;
	@Exported public boolean downloadAllowed;
	@Exported public boolean useChangelog;
	@Exported public boolean cleanupOld;
	@Exported public String numberOldVersions;
	@Exported public boolean useAppVersionURL;
	@Exported public boolean debugMode;
	@Exported public boolean useNotesTypeMarkdown;
    @Exported public String releaseNotesFileName;

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    @DataBoundConstructor
	public HockeyappRecorder(String apiToken, String appId, boolean notifyTeam,
			String buildNotes, String filePath, String dsymPath, String tags,
			boolean downloadAllowed, boolean useChangelog, boolean cleanupOld,
			String numberOldVersions, boolean useAppVersionURL, boolean debugMode,
			boolean useNotesTypeMarkdown, String releaseNotesFileName) {

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
		this.numberOldVersions = Util.fixEmptyAndTrim(numberOldVersions);
		this.useAppVersionURL = useAppVersionURL;
		this.debugMode = debugMode;
		this.useNotesTypeMarkdown = useNotesTypeMarkdown;
		this.releaseNotesFileName = Util.fixEmptyAndTrim(releaseNotesFileName);
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

			HttpClient httpclient = createPreconfiguredHttpClient();
            HttpPost httpPost;
            if(useAppVersionURL) {
                if (appId == null) {
                    listener.getLogger().println(Messages.APP_ID_MISSING());
                    return false;
                }
                httpPost = new HttpPost(
                        "https://rink.hockeyapp.net/api/2/apps/" + vars.expand(appId) +"/app_versions");
            } else {
                httpPost = new HttpPost(
                        "https://rink.hockeyapp.net/api/2/apps/upload");

            }
			FileBody fileBody = new FileBody(file);
			httpPost.setHeader("X-HockeyAppToken", vars.expand(fetchApiToken()));
			MultipartEntity entity = new MultipartEntity();
			if (useChangelog) {
			//StringBuilder sb = new StringBuilder(super.buildCompletionMessage(publisher,build,listener));
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
            } else if (buildNotes != null) {
                entity.addPart("notes", new StringBody(vars.expand(buildNotes), UTF8_CHARSET));
                entity.addPart("notes_type", new StringBody(useNotesTypeMarkdown ? "1" : "0"));
            } else if (releaseNotesFileName != null) {
                File releaseNotesFile = getFileLocally(build.getWorkspace(), vars.expand(releaseNotesFileName), tempDir);
                listener.getLogger().println(releaseNotesFile);
                String releaseNotes = readReleaseNotesFile(releaseNotesFile);
                entity.addPart("notes", new StringBody(releaseNotes, UTF8_CHARSET));
                entity.addPart("notes_type", new StringBody(useNotesTypeMarkdown ? "1" : "0"));
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
			HttpResponse response = httpclient.execute(httpPost);
			HttpEntity resEntity = response.getEntity();

			InputStream is = resEntity.getContent();

			// Improved error handling.
			if (response.getStatusLine().getStatusCode() != 201) {
				String responseBody = new Scanner(is).useDelimiter("\\A")
						.next();
				listener.getLogger().println(
						Messages.UNEXPECTED_RESPONSE_CODE(response.getStatusLine().getStatusCode()));
				listener.getLogger().println(responseBody);
				return false;
			} else if(isDebugEnabled()) { // DEBUG MODE output
				String responseBody = new Scanner(is).useDelimiter("\\A").next();
				listener.getLogger().println("RESPONSE: " + responseBody);
			}

			JSONParser parser = new JSONParser();

			final Map parsedMap = (Map) parser.parse(new BufferedReader(
					new InputStreamReader(is)));

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

			if (cleanupOld) {
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
				cleanupOldVersions(listener, vars);
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
				Result r = ((AbstractBuild<?,?>) o).getResult();
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

	private boolean cleanupOldVersions(BuildListener listener, EnvVars vars) {
		try {
			HttpClient httpclient = createPreconfiguredHttpClient();
			HttpPost httpPost = new HttpPost(
			        "https://rink.hockeyapp.net/api/2/apps/" + vars.expand(appId)
						+ "/app_versions/delete");
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
					                response.getStatusLine().getStatusCode()));
					listener.getLogger().println(responseBody);
					return false;
				}

				JSONParser parser = new JSONParser();
				final Map parsedMap = (Map) parser.parse(
				        new BufferedReader(new InputStreamReader(is)));
				listener.getLogger().println(
					Messages.DELETED_OLD_VERSIONS(String.valueOf(
					        parsedMap.get("total_entries"))));
			}
		} catch (Exception e) {
			e.printStackTrace(listener.getLogger());
			return false;
		}
		return true;
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

		public Boolean getGlobalDebugMode() {
			return this.globalDebugMode;

		}

		@SuppressWarnings("unused") // Used by Jenkins
		public void setGlobalDebugMode(Boolean globalDebugMode) {
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
