package net.welights.tools.dbupgrade.common;

import java.util.List;

public class StorageModuleVo {
    private String moduleName;
    private String modulePath;
    private List<StorageModuleVersionVo> versionList;

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModulePath() {
        return modulePath;
    }

    public void setModulePath(String modulePath) {
        this.modulePath = modulePath;
    }

    public List<StorageModuleVersionVo> getVersionList() {
        return versionList;
    }

    public void setVersionList(List<StorageModuleVersionVo> versionList) {
        this.versionList = versionList;
    }

}
