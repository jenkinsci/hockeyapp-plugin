package hockeyapp;

import org.apache.http.client.methods.HttpPost;

class HttpInfo {
    private String path;
    private String method = HttpPost.METHOD_NAME;

    HttpInfo() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
