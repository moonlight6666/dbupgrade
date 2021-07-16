package net.welights.tools.dbupgrade.common;

/**
 * @author welights
 */
public class ModuleVo {

    private String module;
    private String status;
    private String error;
    private String version;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return module + " " + version;
    }

}
