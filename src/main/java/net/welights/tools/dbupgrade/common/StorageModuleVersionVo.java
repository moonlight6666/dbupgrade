package net.welights.tools.dbupgrade.common;

import java.util.List;

/**
 * @author welights
 */
public class StorageModuleVersionVo extends StorageModuleVo {

    private String versionName;
    private String versionPath;
    private List<StorageModuleVersionScriptVo> ddlList;
    private List<StorageModuleVersionScriptVo> dmlList;

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getVersionPath() {
        return versionPath;
    }

    public void setVersionPath(String versionPath) {
        this.versionPath = versionPath;
    }

    public List<StorageModuleVersionScriptVo> getDdlList() {
        return ddlList;
    }

    public void setDdlList(List<StorageModuleVersionScriptVo> ddlList) {
        this.ddlList = ddlList;
    }

    public List<StorageModuleVersionScriptVo> getDmlList() {
        return dmlList;
    }

    public void setDmlList(List<StorageModuleVersionScriptVo> dmlList) {
        this.dmlList = dmlList;
    }

}
