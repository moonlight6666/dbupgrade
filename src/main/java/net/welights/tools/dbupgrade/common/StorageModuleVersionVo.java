package net.welights.tools.dbupgrade.common;

import java.util.List;

public class StorageModuleVersionVo {

    private String VersionName;
    private String VersionPath;
    private List<StorageModuleVersionScriptVo> ddlList;
    private List<StorageModuleVersionScriptVo> dmlList;

    public String getVersionName() {
        return VersionName;
    }

    public void setVersionName(String versionName) {
        VersionName = versionName;
    }

    public String getVersionPath() {
        return VersionPath;
    }

    public void setVersionPath(String versionPath) {
        VersionPath = versionPath;
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
