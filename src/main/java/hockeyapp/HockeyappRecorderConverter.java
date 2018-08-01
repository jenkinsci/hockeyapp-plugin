package hockeyapp;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Util;
import jenkins.model.Jenkins;
import net.hockeyapp.jenkins.RadioButtonSupport;
import net.hockeyapp.jenkins.releaseNotes.ChangelogReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.FileReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.ManualReleaseNotes;
import net.hockeyapp.jenkins.releaseNotes.NoReleaseNotes;
import net.hockeyapp.jenkins.uploadMethod.AppCreation;
import net.hockeyapp.jenkins.uploadMethod.VersionCreation;

import java.util.Collections;
import java.util.List;

public class HockeyappRecorderConverter implements Converter {
    private static final XStream XSTREAM = Jenkins.XSTREAM;

    @Override
    public boolean canConvert(Class clazz) {
        return clazz.equals(HockeyappRecorder.class);
    }

    public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        Converter converter = new ReflectionConverter(XSTREAM.getMapper(), XSTREAM.getReflectionProvider());
        converter.marshal(value, writer, context);
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        final long version = unmarshalPluginVersion(reader);
        if (version > 0) {
            Converter converter = new ReflectionConverter(XSTREAM.getMapper(), XSTREAM.getReflectionProvider());
            return converter.unmarshal(reader, context);
        }

        HockeyappRecorderObsolete recorderObsolete = (HockeyappRecorderObsolete) context.convertAnother(
                context.currentObject(), HockeyappRecorderObsolete.class);

        HockeyappApplication application = new HockeyappApplication(recorderObsolete.apiToken, recorderObsolete.appId,
                recorderObsolete.notifyTeam, recorderObsolete.filePath, recorderObsolete.dsymPath,
                null, recorderObsolete.tags, recorderObsolete.teams, recorderObsolete.mandatory,
                recorderObsolete.downloadAllowed, recorderObsolete.oldVersionHolder,
                recorderObsolete.releaseNotesMethod, recorderObsolete.uploadMethod);
        final List<HockeyappApplication> applications = Collections.singletonList(application);
        return new HockeyappRecorder(applications, recorderObsolete.debugMode,
                new HockeyappRecorder.BaseUrlHolder(recorderObsolete.baseUrl), recorderObsolete.failGracefully);
    }

    private long unmarshalPluginVersion(HierarchicalStreamReader reader) {
        final String versionString = reader.getAttribute("schemaVersion");
        return versionString == null ? 0L : Long.parseLong(versionString);
    }

    private static final class HockeyappRecorderObsolete {
        public String apiToken;
        public String appId;
        public boolean notifyTeam;
        public String buildNotes;
        public String filePath;
        public String dsymPath;
        public String tags;
        public String teams;
        public boolean mandatory;
        public boolean downloadAllowed;
        public boolean useChangelog;
        public String numberOldVersions;
        public String sortOldVersions;
        public String strategyOldVersions;
        public boolean useAppVersionURL;
        public boolean debugMode;
        public boolean useNotesTypeMarkdown;
        public String releaseNotesFileName;
        public RadioButtonSupport uploadMethod;
        public String baseUrl;
        public RadioButtonSupport releaseNotesMethod;
        public boolean failGracefully;
        public HockeyappApplication.OldVersionHolder oldVersionHolder;

        public HockeyappRecorderObsolete() {
            super();
        }

        @SuppressWarnings("unused")
        private Object readResolve() {
            if (releaseNotesMethod == null) {
                buildNotes = Util.fixEmptyAndTrim(buildNotes);
                releaseNotesFileName = Util.fixEmptyAndTrim(releaseNotesFileName);

                if (buildNotes != null) {
                    releaseNotesMethod = new ManualReleaseNotes(buildNotes, useNotesTypeMarkdown);
                } else if (releaseNotesFileName != null) {
                    releaseNotesMethod = new FileReleaseNotes(releaseNotesFileName, useNotesTypeMarkdown);
                } else if (useChangelog) {
                    releaseNotesMethod = new ChangelogReleaseNotes();
                } else {
                    releaseNotesMethod = new NoReleaseNotes();
                }
            }

            appId = Util.fixEmptyAndTrim(appId);
            if (uploadMethod == null) {
                if (useAppVersionURL && (appId != null)) {
                    uploadMethod = new VersionCreation(appId, "");
                } else {
                    uploadMethod = new AppCreation(false);
                }
            }

            if (oldVersionHolder == null) {
                oldVersionHolder =
                        new HockeyappApplication.OldVersionHolder(Util.fixEmptyAndTrim(numberOldVersions), Util.fixEmptyAndTrim(sortOldVersions), Util.fixEmptyAndTrim(strategyOldVersions));
            }

            return this;
        }
    }
}
