package hockeyapp.builder;

import hockeyapp.HockeyappApplication;
import hockeyapp.HockeyappRecorder;

import java.util.Collections;
import java.util.List;

public class HockeyappRecorderBuilder {

    private List<HockeyappApplication> applications = Collections.emptyList();
    private boolean debugMode = false;
    private HockeyappRecorder.BaseUrlHolder baseUrlHolder =
            new HockeyappRecorder.BaseUrlHolder("http://fake.url:1234/");
    private boolean failGracefully = false;

    public HockeyappRecorderBuilder setLocalhostBaseUrl(int port) {
        this.baseUrlHolder = new HockeyappRecorder.BaseUrlHolder("http://localhost:" + port + "/");
        return this;
    }

    public HockeyappRecorderBuilder setApplications(List<HockeyappApplication> applications) {
        this.applications = applications;
        return this;
    }

    public HockeyappRecorderBuilder setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    public HockeyappRecorderBuilder setFailGracefully(boolean failGracefully) {
        this.failGracefully = failGracefully;
        return this;
    }

    public HockeyappRecorder create() {
        return new HockeyappRecorder(applications, debugMode, baseUrlHolder, failGracefully);
    }
}