package net.welights.tools.dbupgrade.core;

import com.beust.jcommander.JCommander;
import com.vdurmont.semver4j.Semver;

import net.welights.tools.dbupgrade.DatabaseUpgrade;
import net.welights.tools.dbupgrade.common.Constants;
import net.welights.tools.dbupgrade.common.ModuleVo;
import net.welights.tools.dbupgrade.common.ScriptVo;
import net.welights.tools.dbupgrade.common.StorageModuleVersionScriptVo;
import net.welights.tools.dbupgrade.common.StorageModuleVersionVo;
import net.welights.tools.dbupgrade.common.StorageModuleVo;
import net.welights.tools.dbupgrade.util.ProcessUtil;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

/**
 * @author welights
 */
public class UpgradeProcess {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final PrintStream LOGGER = System.out;
    private final String host;
    private final String username;
    private final String password;
    private final String database;
    private static Connection con = null;
    private static ScriptRunner scriptExecutor;
    List<String> succeedList = new ArrayList<>();
    private final String sqlPath;

    public UpgradeProcess(DatabaseUpgrade cli) {
        host = cli.getHost();
        username = cli.getUsername();
        password = cli.getPassword();
        database = cli.getDatabase();
        sqlPath = cli.getParameters().get(0);
    }

    private static String executeSql(ScriptRunner scriptExecutor, StorageModuleVersionScriptVo storageModuleVersionScriptVo) throws FileNotFoundException {
        LOGGER.println("        执行  " + storageModuleVersionScriptVo.getType() + " >> " + storageModuleVersionScriptVo.getScriptName());
        LOGGER.println("");

        while (true) {
            LOGGER.print("    请点击回车或输入 y 确认执行 , 输入 n 跳过 ， 输入 q 退出: >> ");
            String inputJar = SCANNER.nextLine();
            if ("y".equals(inputJar) || StringUtils.isBlank(inputJar)) {
                FileReader sqlReader = new FileReader(storageModuleVersionScriptVo.getScriptPath());
                scriptExecutor.runScript(sqlReader);
                return Constants.STATUS_SUCCEED;
            } else if ("n".equals(inputJar)) {
                return Constants.STATUS_SKIP;
            } else if ("q".equals(inputJar)) {
                return Constants.STATUS_INTERRUPT;
            }
        }
    }

    private static void close() {
        if (con == null) {
            return;
        }
        try {
            con.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        } finally {
            con = null;
        }
    }

    public void processUpgrade() throws SQLException {

        JCommander.getConsole().println("host:     " + host);
        JCommander.getConsole().println("username: " + username);
        JCommander.getConsole().println("database: " + database);
        JCommander.getConsole().println("sql path: " + sqlPath);

        // step 1: create table for save sql exec status and log
        initAuditTable();

        // step 2: find sql to execute
        List<StorageModuleVo> storageModuleVoList = analysisModule();

        printVersionInfo(storageModuleVoList);

        loadRunner();
        LOGGER.print("    请按回车或输入 y 继续 , 输入 q 退出: >> ");
        while (true) {
            String inputNext = SCANNER.nextLine();
            if (inputNext.equals("y") || StringUtils.isBlank(inputNext)) {
                break;
            } else if (inputNext.equals("q")) {
                LOGGER.println("    Bye.");
                return;
            } else {
                LOGGER.println("    请输入正确的命令");
            }
        }

        while (true) {
            printHelp(storageModuleVoList);
            String inputModule = SCANNER.nextLine();
            List<String> requireUpgradeList = new ArrayList<>();

            if (!ProcessUtil.readyToContinue(inputModule, storageModuleVoList, requireUpgradeList)) {
                continue;
            }

            //升级模块
            for (int i = 0; i < requireUpgradeList.size(); i++) {
                String numberIndex = requireUpgradeList.get(i);
                StorageModuleVo storageModuleVo = storageModuleVoList.get(Integer.parseInt(numberIndex));
                LOGGER.println();
                LOGGER.println("开始升级 " + storageModuleVo.getModuleName() + " ....................");
                List<StorageModuleVersionScriptVo> scriptVoList = new ArrayList<>();
                String status = upgradeModule(storageModuleVo, scriptVoList);
                if (status.equals(Constants.STATUS_SUCCEED)) {
                    succeedList.add(storageModuleVo.getModuleName());
                }

                LOGGER.println("升级结果清单:");
                for (StorageModuleVersionScriptVo scriptVo : scriptVoList) {
                    ScriptVo resultVo = getSqlStatus(scriptVo.getModuleName(), scriptVo.getVersionName(), scriptVo.getScriptName(), scriptVo.getType());
                    LOGGER.println("        * 状态: " + resultVo.getStatus() + ", 版本: " + resultVo.getModuleVersion() + ", 类型: " + resultVo.getType() + ", 脚本名称: " + resultVo.getScriptName());
                    if (StringUtils.isNotBlank(resultVo.getMessage())) {
                        LOGGER.println("        * 信息: " + resultVo.getMessage());
                    }
                }
                LOGGER.println("");
                LOGGER.println("    结束升级 " + storageModuleVo.getModuleName());
                LOGGER.println();
                LOGGER.println();
            }

        }
    }

    private String upgradeModule(StorageModuleVo storageModuleVo, List<StorageModuleVersionScriptVo> scriptVoList) {
        boolean moduleUpgradeStatus = true;
        try {
            List<StorageModuleVersionVo> versionList = storageModuleVo.getVersionList();
            ModuleVo moduleVo = getModuleVersion(storageModuleVo.getModuleName());
            int currentVersionIndex = 0;

            if (moduleVo == null || StringUtils.isBlank(moduleVo.getVersion())) {
                while (true) {
                    LOGGER.println("    没有定义当前版本 , 你可以选择一个版本作为当前版本升级, 版本列表如下:  ");
                    for (int i = 0; i < versionList.size(); i++) {
                        LOGGER.println("        " + versionList.get(i).getVersionName() + "   ");
                    }
                    LOGGER.print("    请输入版本名称  , 输入 s 跳过 ，输入 q 退出: >> ");
                    String inputJar = SCANNER.nextLine();
                    for (int i = 0; i < versionList.size(); i++) {
                        if (StringUtils.equals(versionList.get(i).getVersionName(), inputJar)) {
                            currentVersionIndex = i;
                        }
                    }

                    if (currentVersionIndex >= 0) {
                        break;
                    } else if ("s".equals(inputJar)) {
                        return Constants.STATUS_SKIP;
                    } else if ("q".equals(inputJar)) {
                        return Constants.STATUS_INTERRUPT;
                    } else {
                        LOGGER.println("    请检查你的输入 ");
                    }
                }
            }

            for (int i = currentVersionIndex; i < versionList.size(); i++) {
                boolean moduleVersionUpgradeStatus = true;
                StorageModuleVersionVo storageModuleVersionVo = versionList.get(i);
                String nextVersion = storageModuleVersionVo.getVersionName();
                if (moduleVo != null
                        && StringUtils.equals(storageModuleVersionVo.getVersionName(), moduleVo.getVersion())
                        && !StringUtils.equals(Constants.STATUS_FAILED, moduleVo.getStatus())) {
                    continue;
                }
                try {
                    LOGGER.println("        升级到版本:  " + nextVersion);

                    List<StorageModuleVersionScriptVo> ddlList = storageModuleVersionVo.getDdlList();
                    if (!ddlList.isEmpty()) {
                        for (StorageModuleVersionScriptVo ddl : ddlList) {
                            try {
                                // 避免执行成功的sql重复执行
                                String sqlStatus = getSqlStatus(storageModuleVo.getModuleName(), nextVersion, ddl.getScriptName(), "ddl").getStatus();
                                if (!StringUtils.equals(sqlStatus, Constants.STATUS_SUCCEED)) {
                                    String sqlExecStatus = executeSql(scriptExecutor, ddl);
                                    updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, ddl.getScriptName(), "ddl", sqlExecStatus, "");
                                }
                            } catch (Exception e) {
                                moduleUpgradeStatus = false;
                                updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, ddl.getScriptName(), "ddl", Constants.STATUS_FAILED, e.getMessage());
                            } finally {
                                LOGGER.println("");
                                LOGGER.println("");
                            }
                            scriptVoList.add(ddl);
                        }
                    }

                    List<StorageModuleVersionScriptVo> dmlList = storageModuleVersionVo.getDmlList();
                    if (!dmlList.isEmpty()) {
                        for (StorageModuleVersionScriptVo dml : dmlList) {
                            try {
                                //避免执行成功的sql重复执行
                                String sqlStatus = getSqlStatus(storageModuleVo.getModuleName(), nextVersion, dml.getScriptName(), "dml").getStatus();
                                if (!StringUtils.equals(sqlStatus, Constants.STATUS_SUCCEED)) {
                                    String sqlExecStatus = executeSql(scriptExecutor, dml);
                                    updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, dml.getScriptName(), "dml", sqlExecStatus, "");
                                }
                            } catch (Exception e) {
                                moduleUpgradeStatus = false;
                                updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, dml.getScriptName(), "dml", Constants.STATUS_FAILED, e.getMessage());
                            } finally {
                                LOGGER.println("");
                                LOGGER.println("");
                            }
                            scriptVoList.add(dml);
                        }
                    }

                } catch (Exception e) {
                    moduleUpgradeStatus = false;
                    moduleVersionUpgradeStatus = false;
                }
                updateModuleVersion(storageModuleVo.getModuleName(), nextVersion, moduleVersionUpgradeStatus ? Constants.STATUS_SUCCEED : Constants.STATUS_FAILED);
            }
        } catch (Exception e) {
            moduleUpgradeStatus = false;
            LOGGER.println(e.getMessage());
        }

        return moduleUpgradeStatus ? Constants.STATUS_SUCCEED : Constants.STATUS_FAILED;
    }

    private void printHelp(List<StorageModuleVo> storageModuleVoList) {
        LOGGER.println("");
        LOGGER.println("模块列表: ");
        for (int i = 0; i < storageModuleVoList.size(); i++) {
            StorageModuleVo moduleVo = storageModuleVoList.get(i);
            LOGGER.println("        * " + i + " " + moduleVo.getModuleName());

        }
        LOGGER.println("");
        LOGGER.print("    请输入对应的数字升级模块，多个用逗号隔开，升级全部直接回车，输入q 退出: >> ");
    }

    private void printVersionInfo(List<StorageModuleVo> storageModuleVoList) {
        LOGGER.println("版本信息:");

        storageModuleVoList.forEach(m -> {
            LOGGER.println("    模块:" + m.getModuleName());
            try {
                ModuleVo module = getModuleVersion(m.getModuleName());
                if (!m.getVersionList().isEmpty()) {
                    m.getVersionList().forEach(v -> {
                        if (module != null && module.getVersion().equals(v.getVersionName())) {
                            LOGGER.println("        |-  " + v.getVersionName() + " <<< 当前版本，上一次执行状态: " + module.getStatus());
                        } else {
                            LOGGER.println("        |-  " + v.getVersionName());
                        }
                    });
                } else {
                    LOGGER.println("        |-  暂无版本信息");
                }
            } catch (SQLException e) {
                LOGGER.println(e.getMessage());
            }

        });
        LOGGER.println("");
    }

    private List<StorageModuleVersionVo> getModuleVersions(String moduleName) {
        File moduleDir = new File(sqlPath + "/" + moduleName);
        List<StorageModuleVersionVo> versionList = new ArrayList<>();
        if (moduleDir.isDirectory()) {
            File[] moduleVersions = moduleDir.listFiles();
            if (moduleVersions != null && moduleVersions.length > 0) {
                for (File moduleVersionDir : moduleVersions) {
                    StorageModuleVersionVo storageModuleVersionVo = new StorageModuleVersionVo();
                    storageModuleVersionVo.setModuleName(moduleName);
                    storageModuleVersionVo.setVersionName(moduleVersionDir.getName());
                    storageModuleVersionVo.setVersionPath(moduleVersionDir.getPath());
                    versionList.add(storageModuleVersionVo);
                }
            }
        }
        versionList.sort(Comparator.comparing(o -> new Semver(o.getVersionName())));

        for (int i = 0; i < versionList.size(); i++) {
            StorageModuleVersionVo versionVo = versionList.get(i);
            String versionPath = versionVo.getVersionPath();
            File versionDir = new File(versionPath);
            File[] typeList = versionDir.listFiles();

            List<StorageModuleVersionScriptVo> ddlList = new ArrayList<>();
            List<StorageModuleVersionScriptVo> dmlList = new ArrayList<>();

            assert typeList != null;
            for (File type : typeList) {
                if (StringUtils.contains(type.getName(), "DDL")) {
                    File[] ddlFileList = type.listFiles();
                    assert ddlFileList != null;
                    for (File ddl : ddlFileList) {
                        StorageModuleVersionScriptVo storageModuleVersionScriptVo = new StorageModuleVersionScriptVo();
                        storageModuleVersionScriptVo.setModuleName(moduleName);
                        storageModuleVersionScriptVo.setVersionName(versionVo.getVersionName());
                        storageModuleVersionScriptVo.setScriptName(ddl.getName());
                        storageModuleVersionScriptVo.setScriptPath(ddl.getPath());
                        storageModuleVersionScriptVo.setType("ddl");
                        ddlList.add(storageModuleVersionScriptVo);
                    }
                }

                if (StringUtils.contains(type.getName(), "DML")) {
                    File[] dmlFileList = type.listFiles();
                    assert dmlFileList != null;
                    for (File dml : dmlFileList) {
                        StorageModuleVersionScriptVo storageModuleVersionScriptVo = new StorageModuleVersionScriptVo();
                        storageModuleVersionScriptVo.setModuleName(moduleName);
                        storageModuleVersionScriptVo.setVersionName(versionVo.getVersionName());
                        storageModuleVersionScriptVo.setScriptName(dml.getName());
                        storageModuleVersionScriptVo.setScriptPath(dml.getPath());
                        storageModuleVersionScriptVo.setType("dml");
                        dmlList.add(storageModuleVersionScriptVo);
                    }
                }


            }
            versionVo.setDdlList(ddlList);
            versionVo.setDmlList(dmlList);
        }
        return versionList;
    }

    private List<StorageModuleVo> analysisModule() {
        List<StorageModuleVo> moduleInfo = new ArrayList<>();
        File libDir = new File(sqlPath);
        if (libDir.isDirectory()) {
            File[] modules = libDir.listFiles();
            if (modules != null && modules.length > 0) {
                for (File f : modules) {
                    if (f.getName().startsWith("module-")) {
                        String moduleName = f.getName();
                        StorageModuleVo storageModuleVo = new StorageModuleVo();
                        List<StorageModuleVersionVo> versionList = getModuleVersions(moduleName);
                        storageModuleVo.setModuleName(moduleName);
                        storageModuleVo.setModulePath(f.getPath());
                        storageModuleVo.setVersionList(versionList);
                        moduleInfo.add(storageModuleVo);
                    }
                }
            }
        }
        moduleInfo.sort(Comparator.comparing(StorageModuleVo::getModuleName));
        return moduleInfo;
    }

    private void loadRunner() throws SQLException {
        // exec sql , auto commit = false
        Connection sqlConnection = DriverManager.getConnection(
                String.format("jdbc:mysql://%s/%s?characterEncoding=UTF-8", host, database),
                username, password);
        scriptExecutor = new ScriptRunner(sqlConnection);
        scriptExecutor.setSendFullScript(true);
        scriptExecutor.setAutoCommit(false);
        scriptExecutor.setStopOnError(true);
    }

    private void open() throws SQLException {
        if (con != null) {
            return;
        }
        con = DriverManager.getConnection(
                String.format("jdbc:mysql://%s/%s?characterEncoding=UTF-8", host, database),
                username, password);
        con.setAutoCommit(true);
    }

    private void initAuditTable() throws SQLException {
        open();
        try (PreparedStatement versionsStatement = con.prepareStatement(Constants.CHECK_VERSION_TABLE);
             PreparedStatement auditStatement = con.prepareStatement(Constants.CHECK_AUDIT_TABLE)) {
            versionsStatement.executeUpdate();
            auditStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private ModuleVo getModuleVersion(String moduleName) throws SQLException {
        ModuleVo libVo = null;
        open();
        try (PreparedStatement st = con.prepareStatement(Constants.GET_DB_BASELINE)) {
            st.setString(1, moduleName);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                libVo = new ModuleVo();
                libVo.setModule(rs.getString("module"));
                libVo.setVersion(rs.getString("version"));
                libVo.setStatus(rs.getString("status"));
            }
            rs.close();
        } finally {
            close();
        }
        return libVo;
    }

    private void updateModuleVersion(String module, String version, String status) throws SQLException {
        open();
        try (PreparedStatement st = con.prepareStatement(Constants.UPDATE_DB_BASELINE)) {
            st.setString(1, module);
            st.setString(2, version);
            st.setString(3, status);
            st.executeUpdate();
        } finally {
            close();
        }
    }

    private ScriptVo getSqlStatus(String moduleName, String moduleVersion, String scriptName, String type) {
        ScriptVo scriptVo = new ScriptVo();
        scriptVo.setModuleName(moduleName);
        scriptVo.setModuleVersion(moduleVersion);
        scriptVo.setType(type);
        scriptVo.setScriptName(scriptName);
        try {

            open();
            PreparedStatement st = con.prepareStatement(Constants.CHECK_SQL_STATUS);
            st.setString(1, moduleName);
            st.setString(2, moduleVersion);
            st.setString(3, scriptName);
            st.setString(4, type);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                String status = rs.getString("status");
                String message = rs.getString("message");
                scriptVo.setMessage(message);
                scriptVo.setStatus(status);
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(moduleName + " 获取版本信息失败, " + e.getMessage());
        } finally {
            close();
        }
        return scriptVo;
    }

    private void updateSqlStatus(String moduleName, String moduleVersion, String scriptName, String type, String status, String message) throws SQLException {
        open();
        try (PreparedStatement st = con.prepareStatement(Constants.UPDATE_SQL_STATUS);) {
            st.setString(1, moduleName);
            st.setString(2, moduleVersion);
            st.setString(3, scriptName);
            st.setString(4, type);
            st.setString(5, status);
            st.setString(6, message);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(moduleName + " 更新版本信息失败, " + e.getMessage());
        } finally {
            close();
        }
    }

}
