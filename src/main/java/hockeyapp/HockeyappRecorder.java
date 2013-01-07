package hockeyapp;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.AbstractBuild;
import hudson.tasks.*;
import hudson.util.RunList;
import org.apache.commons.collections.Predicate;
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
import org.json.simple.parser.JSONParser;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.scm.ChangeLogSet.Entry;
import java.io.*;
import java.util.*;

import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;

public class HockeyappRecorder extends Recorder {
	private String apiToken;

	public String getApiToken() {
		return this.apiToken;
	}

	private String appId;

	public String getAppId() {
		return this.appId;
	}

	private Boolean notifyTeam;

	public Boolean getNotifyTeam() {
		return this.notifyTeam;
	}

	private String buildNotes;

	public String getBuildNotes() {
		return this.buildNotes;
	}

	private String filePath;

	public String getFilePath() {
		return this.filePath;
	}

	private String dsymPath;

	public String getDsymPath() {
		return this.dsymPath;
	}

	private String tags;

	public String getTags() {
		return this.tags;
	}

	private Boolean downloadAllowed;

	public Boolean getDownloadAllowed() {
		return this.downloadAllowed;
	}

	private Boolean useChangelog;

	public Boolean getUseChangelog() {
		return this.useChangelog;
	}

	private Boolean cleanupOld;

	public Boolean getCleanupOld() {
		return this.cleanupOld;
	}

	private String numberOldVersions;

	public String getNumberOldVersions() {
		return this.numberOldVersions;
	}

    private Boolean useAppVersionURL;

    public Boolean getUseAppVersionURL() {
        return useAppVersionURL;
    }

    @DataBoundConstructor
	public HockeyappRecorder(String apiToken, String appId, Boolean notifyTeam,
			String buildNotes, String filePath, String dsymPath, String tags,
			Boolean downloadAllowed, Boolean useChangelog, Boolean cleanupOld,
			String numberOldVersions,Boolean useAppVersionURL) {
		this.apiToken = apiToken;
		this.appId = appId;
		this.notifyTeam = notifyTeam;
		this.buildNotes = buildNotes;
		this.filePath = filePath;
		this.dsymPath = dsymPath;
		this.tags = tags;
		this.downloadAllowed = downloadAllowed;
		this.useChangelog = useChangelog;
		this.cleanupOld = cleanupOld;
		this.numberOldVersions = numberOldVersions;
        this.useAppVersionURL = useAppVersionURL;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
			return false;

		listener.getLogger().println("Uploading to hockeyapp");
		File tempDir = null;
		try {
			EnvVars vars = build.getEnvironment(listener);

			// Copy remote file to local file system.
			tempDir = File.createTempFile("jtf", null);
			tempDir.delete();
			tempDir.mkdirs();

			File file = getFileLocally(build.getWorkspace(),
					vars.expand(filePath), tempDir);
			listener.getLogger().println(file);

			HttpClient httpclient = new DefaultHttpClient();
            HttpPost httpPost;
            if(useAppVersionURL) {
                if( StringUtils.isBlank(appId)) {
                    listener.getLogger().println("appID is blank, can not build AppVersion upload URL. Set appID or disable useAppVersionURL.");
                    return false;
                }
                httpPost = new HttpPost(
                        "https://rink.hockeyapp.net/api/2/apps/" + appId +"/app_versions");
            } else {
                httpPost = new HttpPost(
                        "https://rink.hockeyapp.net/api/2/apps/upload");

            }
			FileBody fileBody = new FileBody(file);
			httpPost.setHeader("X-HockeyAppToken", apiToken);
			MultipartEntity entity = new MultipartEntity();
			if (!useChangelog) {
			entity.addPart("notes", new StringBody(vars.expand(buildNotes)));
			} else {
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
			 entity.addPart("notes", new StringBody(sb.toString()));
			}
			entity.addPart("notes_type", new StringBody("0"));

			entity.addPart("ipa", fileBody);

			if (!StringUtils.isEmpty(dsymPath)) {
				File dsymFile = getFileLocally(build.getWorkspace(),
						vars.expand(dsymPath), tempDir);
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
						"Incorrect response code: "
								+ response.getStatusLine().getStatusCode());
				listener.getLogger().println(responseBody);
				return false;
			}

			JSONParser parser = new JSONParser();

			final Map parsedMap = (Map) parser.parse(new BufferedReader(
					new InputStreamReader(is)));

			HockeyappBuildAction installAction = new HockeyappBuildAction();
			installAction.displayName = "Hockeyapp Install Link";
			installAction.iconFileName = "package.gif";
			installAction.urlName = (String) parsedMap.get("public_url");
			build.addAction(installAction);

			HockeyappBuildAction configureAction = new HockeyappBuildAction();
			configureAction.displayName = "Hockeyapp Configuration Link";
			configureAction.iconFileName = "gear2.gif";
			configureAction.urlName = (String) parsedMap.get("config_url");
			build.addAction(configureAction);

			if (cleanupOld) {
				if (StringUtils.isBlank(appId)) {
					listener.getLogger().println(
							"No Public ID / App ID specified!");
					listener.getLogger().println("Aborting cleanup");
					return false;
				}
				if (StringUtils.isBlank(numberOldVersions)) {
					listener.getLogger().println(
							"No number of old versions to keep specified!");
					listener.getLogger().println("Aborting cleanup");
					return false;
				}
				cleanupOldVersions(listener);
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

	private File getFileLocally(FilePath workingDir, String strFile,
			File tempDir) throws IOException, InterruptedException {
		if (workingDir.isRemote()) {
			FilePath remoteFile = new FilePath(workingDir, strFile);
			File file = new File(tempDir, remoteFile.getName());
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			remoteFile.copyTo(fos);
			fos.close();
			return file;
		} else {
			return new File(strFile);
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
				return ((AbstractBuild<?, ?>) o).getResult().isBetterOrEqualTo(
						Result.SUCCESS);
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

	private boolean cleanupOldVersions(BuildListener listener) {
		if (StringUtils.isNumeric(numberOldVersions)) {
			if (Integer.parseInt(numberOldVersions) < 1) {
				listener.getLogger().println("You need to keep min 1 Version!");
				listener.getLogger().println("Aborting cleanup");
			}
			try {
				HttpClient httpclient = new DefaultHttpClient();
				HttpPost httpPost = new HttpPost(
						"https://rink.hockeyapp.net/api/2/apps/" + appId
								+ "/app_versions/delete");
				httpPost.setHeader("X-HockeyAppToken", apiToken);
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(
						1);
				nameValuePairs.add(new BasicNameValuePair("keep",
						numberOldVersions));
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
								"Incorrect response code: "
										+ response.getStatusLine()
												.getStatusCode());
						listener.getLogger().println(responseBody);
						return false;
					}

					JSONParser parser = new JSONParser();

					final Map parsedMap = (Map) parser
							.parse(new BufferedReader(new InputStreamReader(is)));
					listener.getLogger().println(
							"Deleted Versions: "
									+ String.valueOf(parsedMap
											.get("total_entries")));
				}
			} catch (Exception e) {
				e.printStackTrace(listener.getLogger());
				return false;
			}
			return true;
		}
		return false;
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
			return "Upload to Hockeyapp";
		}
	}
}
