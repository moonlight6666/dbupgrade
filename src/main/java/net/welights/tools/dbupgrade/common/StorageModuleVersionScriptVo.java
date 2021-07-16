package net.welights.tools.dbupgrade.common;

/**
 * @author welights
 */
public class StorageModuleVersionScriptVo extends StorageModuleVersionVo {
    private String scriptName;
    private String scriptPath;
    private String type;

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
