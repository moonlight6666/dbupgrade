package net.welights.tools.dbupgrade.core;

import com.beust.jcommander.JCommander;

import net.welights.tools.dbupgrade.DatabaseUpgrade;
import net.welights.tools.dbupgrade.common.Constants;
import net.welights.tools.dbupgrade.common.ModuleVo;
import net.welights.tools.dbupgrade.common.ScriptVo;
import net.welights.tools.dbupgrade.common.StorageModuleVersionScriptVo;
import net.welights.tools.dbupgrade.common.StorageModuleVersionVo;
import net.welights.tools.dbupgrade.common.StorageModuleVo;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class UpgradeProcess {

    private static DatabaseUpgrade cliCopy;
    private static Connection con = null;
    private static ScriptRunner scriptExecutor;
    private static Scanner sc = new Scanner(System.in);
    List<String> succeedList = new ArrayList<>();

    public UpgradeProcess(DatabaseUpgrade cli) {
        cliCopy = cli;
    }

    public void upgrade() throws Exception {

        JCommander.getConsole().println("host:     " + cliCopy.getHost());
        JCommander.getConsole().println("username: " + cliCopy.getUsername());
        JCommander.getConsole().println("database: " + cliCopy.getDatabase());
        JCommander.getConsole().println("sql path: " + cliCopy.getParameters().toString());

        // step 1: load sql runner， if not available , quit
        loadRunner();

        // step 2: create table for save sql exec status and log
        initTable();

        // step 3: find sql to execute
        List<StorageModuleVo> storageModuleVoList = analysisModule();

        printVersionInfo(storageModuleVoList);
        System.out.print("    请按回车或输入 y 继续 , 输入 q 退出 ");
        while (true) {
            String inputNext = sc.nextLine();
            if (inputNext.equals("y") || StringUtils.isBlank(inputNext)) {
                break;
            } else if (inputNext.equals("q")) {
                return;
            } else {
                System.out.println("    请输入正确的命令");
            }
        }

        while (true) {
            printHelp(storageModuleVoList);
            String inputJar = sc.nextLine();
            List<String> requireUpgradeList = new ArrayList();
            if (StringUtils.isBlank(inputJar)) {
                for (int i = 0; i < storageModuleVoList.size(); i++) {
                    requireUpgradeList.add(i + "");
                }
            } else if (StringUtils.isNumeric(inputJar)) {
                if (Integer.parseInt(inputJar) < storageModuleVoList.size()) {
                    requireUpgradeList.add(inputJar);
                } else {
                    System.err.println("请输入正确的数字");
                    continue;
                }
            } else if (inputJar.contains(",")) {
                String[] numberArray = inputJar.split(",");
                for (String number : numberArray) {
                    if (StringUtils.isNumeric(number) && Integer.parseInt(number) < storageModuleVoList.size()) {
                        requireUpgradeList.add(number);
                    } else {
                        System.err.println("请输入正确的数字");
                    }
                }
            } else if (inputJar.equals("q")) {
                return;
            } else {
                System.err.println("请检查你的输入");
                continue;
            }

            //升级模块
            for (int i = 0; i < requireUpgradeList.size(); i++) {
                String numberIndex = requireUpgradeList.get(i);
                StorageModuleVo storageModuleVo = storageModuleVoList.get(Integer.parseInt(numberIndex));
                System.out.println();
                System.out.println("开始升级 " + storageModuleVo.getModuleName() + " ....................");
                List<ScriptVo> scriptVoList = new ArrayList<>();
                String status = upgradeModule(storageModuleVo);
                if (status.equals(Constants.STATUS_SUCCEED)) {
                    succeedList.add(storageModuleVo.getModuleName());
                }

                System.out.println("升级结果清单:");
                for (ScriptVo scriptVo : scriptVoList) {
                    ScriptVo resultVo = getSqlStatus(scriptVo.getModuleName(), scriptVo.getModuleVersion(), scriptVo.getScriptName(), scriptVo.getType());
                    System.out.println("        * 状态: " + resultVo.getStatus() + ", 版本: " + resultVo.getModuleVersion() + ", 类型: " + resultVo.getType() + ", 脚本名称: " + resultVo.getScriptName());
                    if (StringUtils.isNotBlank(resultVo.getMessage())) {
                        System.out.println("        * 信息: " + resultVo.getMessage());
                    }
                }
                System.out.println("");
                System.out.println("    结束升级 " + storageModuleVo.getModuleName());
                System.out.println();
                System.out.println();
            }

        }

    }

    private static String upgradeModule(StorageModuleVo storageModuleVo) throws Exception {
        boolean moduleUpgradeStatus = true;
        try {
            List<StorageModuleVersionVo> versionList = storageModuleVo.getVersionList();
            ModuleVo moduleVo = getModuleVersion(storageModuleVo.getModuleName());
            int currentVersionIndex = 0;

            if (moduleVo == null || StringUtils.isBlank(moduleVo.getVersion())) {
                while (true) {
                    System.err.println("    没有定义当前版本 , 你可以选择一个版本作为当前版本升级, 版本列表如下:  ");
                    for (int i = 0; i < versionList.size(); i++) {
                        System.out.println("        " + versionList.get(i).getVersionName() + "   ");
                    }
                    System.err.println("    请输入版本名称  , 输入 s 跳过 ，输入 q 退出 ");
                    String inputJar = sc.nextLine();
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
                        System.err.println("    请检查你的输入 ");
                    }
                }
            }

            for (int i = 0; i < versionList.size(); i++) {
                StorageModuleVersionVo storageModuleVersionVo = versionList.get(i);
                String nextVersion = storageModuleVersionVo.getVersionName();
                if (moduleVo != null
                        && StringUtils.equals(storageModuleVersionVo.getVersionName(), moduleVo.getVersion())
                        && !StringUtils.equals(Constants.STATUS_FAILED, moduleVo.getStatus())) {
                    continue;
                }
                try {
                    System.out.println("        升级到版本:  " + nextVersion);

                    List<StorageModuleVersionScriptVo> ddlList = storageModuleVersionVo.getDdlList();
                    if (ddlList != null && ddlList.size() > 0) {
                        for (StorageModuleVersionScriptVo dml : ddlList) {
                            try {
                                // 避免执行成功的sql重复执行
                                String sqlStatus = getSqlStatus(storageModuleVo.getModuleName(), nextVersion, dml.getScriptName(), "ddl").getStatus();
                                if (!StringUtils.equals(sqlStatus, Constants.STATUS_SUCCEED)) {
                                    String sqlExecStatus = executeSql(scriptExecutor, dml);
                                    updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, dml.getScriptName(), "ddl", sqlExecStatus, "");
                                }
                            } catch (Exception e) {
                                moduleUpgradeStatus = false;
                                updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, dml.getScriptName(), "ddl", Constants.STATUS_FAILED, e.getMessage());
                            } finally {
                                System.out.println("");
                                System.out.println("");
                            }
                        }
                    }

                    List<StorageModuleVersionScriptVo> dmlList = storageModuleVersionVo.getDdlList();
                    if (ddlList != null && dmlList.size() > 0) {
                        for (StorageModuleVersionScriptVo ddl : dmlList) {
                            try {
                                //避免执行成功的sql重复执行
                                String sqlStatus = getSqlStatus(storageModuleVo.getModuleName(), nextVersion, ddl.getScriptName(), "dml").getStatus();
                                if (!StringUtils.equals(sqlStatus, Constants.STATUS_SUCCEED)) {
                                    String sqlExecStatus = executeSql(scriptExecutor, ddl);
                                    updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, ddl.getScriptName(), "dml", sqlExecStatus, "");
                                }
                            } catch (Exception e) {
                                moduleUpgradeStatus = false;
                                updateSqlStatus(storageModuleVo.getModuleName(), nextVersion, ddl.getScriptName(), "dml", Constants.STATUS_FAILED, e.getMessage());
                            } finally {
                                System.out.println("");
                                System.out.println("");
                            }
                        }
                    }

                } catch (Exception e) {
                    moduleUpgradeStatus = false;
                }
                updateLibVersion(storageModuleVo.getModuleName(), nextVersion, moduleUpgradeStatus ? Constants.STATUS_SUCCEED : Constants.STATUS_FAILED);

            }
        } catch (Exception e) {
            moduleUpgradeStatus = false;
            System.err.println(e.getMessage());
        }

        return moduleUpgradeStatus ? Constants.STATUS_SUCCEED : Constants.STATUS_FAILED;
    }

    private static String executeSql(ScriptRunner scriptExecutor, StorageModuleVersionScriptVo storageModuleVersionScriptVo) throws Exception {
        System.out.println("        执行 sql " + storageModuleVersionScriptVo.getScriptName());
        System.out.println("");

        while (true) {
            System.err.println("    请点击回车或输入 y 确认执行 , 输入 n 跳过 ， 输入 q 退出 ");
            String inputJar = sc.nextLine();
            if ("y".equals(inputJar) || StringUtils.isBlank(inputJar)) {
                try (FileReader sqlReader = new FileReader(storageModuleVersionScriptVo.getScriptPath())) {
                    scriptExecutor.runScript(sqlReader);
                }
                return Constants.STATUS_SUCCEED;
            } else if ("n".equals(inputJar)) {
                return Constants.STATUS_SKIP;
            } else if ("q".equals(inputJar)) {
                return Constants.STATUS_INTERRUPT;
            }
        }


    }

    private static void printHelp(List<StorageModuleVo> storageModuleVoList) {
        System.out.println("");
        System.out.println("模块列表: ");
        for (int i = 0; i < storageModuleVoList.size(); i++) {
            StorageModuleVo moduleVo = storageModuleVoList.get(i);
            System.out.println("        * " + i + " " + moduleVo.getModuleName());

        }
        System.out.println("");
        System.out.println("    请输入对应的数字升级模块，多个用逗号隔开，升级全部直接回车，输入q 退出");
        System.out.println("");
    }

    private static void printVersionInfo(List<StorageModuleVo> storageModuleVoList) {
        System.out.println("版本信息:");

        storageModuleVoList.forEach(m -> {
            System.out.println(m.getModuleName());
            m.getVersionList().forEach(v -> {
                System.out.println("    " + v.getVersionName());
            });
        });
        System.out.println("");
    }

    private static ScriptVo getSqlStatus(String moduleName, String moduleVersion, String scriptName, String type) {
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

    private static void updateSqlStatus(String moduleName, String moduleVersion, String scriptName, String type, String status, String message) {
        try {
            open();
            PreparedStatement st = con.prepareStatement(Constants.UPDATE_SQL_STATUS);
            st.setString(1, moduleName);
            st.setString(2, moduleVersion);
            st.setString(3, scriptName);
            st.setString(4, type);
            st.setString(5, status);
            st.setString(6, message);
            st.executeUpdate();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(moduleName + " 更新版本信息失败, " + e.getMessage());
        } finally {
            close();
        }
    }

    private static ModuleVo getModuleVersion(String lib) {
        ModuleVo libVo = null;
        try {
            open();
            PreparedStatement st = con.prepareStatement(Constants.GET_DB_BASELINE);
            st.setString(1, lib);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                libVo = new ModuleVo();
                libVo.setModule(rs.getString("module"));
                libVo.setVersion(rs.getString("version"));
                libVo.setStatus(rs.getString("status"));
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(lib + " 获取版本信息失败, " + e.getMessage());
        } finally {
            close();
        }
        return libVo;
    }

    private static List<StorageModuleVersionVo> getModuleVersionList(String moduleName) throws IOException {
        File moduleDir = new File(cliCopy.getParameters().get(0) + "/" + moduleName);

        List<StorageModuleVersionVo> versionList = new ArrayList<>();
        if (moduleDir.isDirectory()) {
            File[] moduleVersions = moduleDir.listFiles();
            if (moduleVersions != null && moduleVersions.length > 0) {
                for (File moduleVersionDir : moduleVersions) {
                    StorageModuleVersionVo storageModuleVersionVo = new StorageModuleVersionVo();
                    storageModuleVersionVo.setVersionName(moduleVersionDir.getName());
                    storageModuleVersionVo.setVersionPath(moduleVersionDir.getPath());
                    versionList.add(storageModuleVersionVo);
                }
            }
        }


        for (int i = 0; i < versionList.size(); i++) {
            StorageModuleVersionVo versionVo = versionList.get(i);
            String versionPath = versionVo.getVersionPath();
            File versionDir = new File(versionPath);
            File[] typeList = versionDir.listFiles();

            List<StorageModuleVersionScriptVo> ddlList = new ArrayList<>();
            List<StorageModuleVersionScriptVo> dmlList = new ArrayList<>();

            for (File type : typeList) {
                if (StringUtils.contains(type.getName(), "DDL")) {
                    File[] ddlFileList = type.listFiles();
                    for (File ddl : ddlFileList) {
                        StorageModuleVersionScriptVo storageModuleVersionScriptVo = new StorageModuleVersionScriptVo();
                        storageModuleVersionScriptVo.setScriptName(ddl.getName());
                        storageModuleVersionScriptVo.setScriptPath(ddl.getPath());
                        ddlList.add(storageModuleVersionScriptVo);
                    }
                }

                if (StringUtils.contains(type.getName(), "DML")) {
                    File[] dmlFileList = type.listFiles();
                    for (File dml : dmlFileList) {
                        StorageModuleVersionScriptVo storageModuleVersionScriptVo = new StorageModuleVersionScriptVo();
                        storageModuleVersionScriptVo.setScriptName(dml.getName());
                        storageModuleVersionScriptVo.setScriptPath(dml.getPath());
                        dmlList.add(storageModuleVersionScriptVo);
                    }
                }


            }
            versionVo.setDdlList(ddlList);
            versionVo.setDmlList(dmlList);
        }
        return versionList;
    }

    private static void updateLibVersion(String module, String version, String status) {
        try {
            open();
            PreparedStatement st = con.prepareStatement(Constants.UPDATE_DB_BASELINE);
            st.setString(1, module);
            st.setString(2, version);
            st.setString(3, status);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(module + " 获取版本信息失败, " + e.getMessage());
        } finally {
            close();
        }
    }

    private static List<StorageModuleVo> analysisModule() throws IOException {
        List<StorageModuleVo> moduleInfo = new ArrayList<>();
        File libDir = new File(cliCopy.getParameters().get(0));
        if (libDir.isDirectory()) {
            File[] modules = libDir.listFiles();
            if (modules != null && modules.length > 0) {
                for (File f : modules) {
                    if (f.getName().startsWith("module-")) {
                        String moduleName = f.getName();
                        StorageModuleVo storageModuleVo = new StorageModuleVo();
                        List<StorageModuleVersionVo> versionList = getModuleVersionList(moduleName);
                        storageModuleVo.setModuleName(moduleName);
                        storageModuleVo.setModulePath(f.getPath());
                        storageModuleVo.setVersionList(versionList);
                        moduleInfo.add(storageModuleVo);
                    }
                }
            }
        }
        return moduleInfo;
    }

    private static void loadRunner() throws Exception {
        //TODO: SUPPORT OTHER DRIVER AND URL
        con = DriverManager.getConnection(
                String.format("jdbc:mysql://%s/%s?characterEncoding=UTF-8", cliCopy.getHost(), cliCopy.getDatabase()),
                cliCopy.getUsername(),
                cliCopy.getPassword());
        scriptExecutor = new ScriptRunner(con);
        scriptExecutor.setSendFullScript(true);
        scriptExecutor.setAutoCommit(false);
        scriptExecutor.setStopOnError(true);
    }

    private static void initTable() {
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

    private static void open() throws SQLException {
        if (con != null) {
            return;
        }
        con = DriverManager.getConnection(
                String.format("jdbc:mysql://%s/%s?characterEncoding=UTF-8",
                        cliCopy.getHost(),
                        cliCopy.getDatabase()
                ),
                cliCopy.getUsername(),
                cliCopy.getPassword());
        con.setAutoCommit(true);
    }

    private static void close() {
//        if (con == null) {
//            return;
//        }
//        try {
//            con.close();
//        } catch (SQLException ex) {
//            ex.printStackTrace();
//        } finally {
//            con = null;
//        }
    }

}
