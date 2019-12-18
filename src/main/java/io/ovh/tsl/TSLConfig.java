package io.ovh.tsl;

public class TSLConfig {
    private String path;
    private String error;

    public TSLConfig(String path, String error) {
        this.path = path;
        this.error = error;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
