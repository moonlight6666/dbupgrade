package dbupgrade;

import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.jdbc.ScriptRunner;

import javax.print.attribute.standard.ReferenceUriSchemesSupported;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class DatabaseUpgrade {

    private static String url;
    private static Driver driver;
    private static String username;
    private static String password;
    private static String driverName;
    private static Connection dbConnection;

    private static String libPath;
    private static String configPath;
    private static String techsureHome;
    private static String instanceName;


    private static Connection con = null;
    private static ScriptRunner scriptExecutor;
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) throws Exception {

        System.out.println("start upgrade...");

        int paramLrngth = args.length;
        if(paramLrngth != 1){
            throw new RuntimeException("please define instance name, like balantflow");
        }

        instanceName = args[0];
        System.out.println("instance name is " +  instanceName);
        try {

            //升级前检查
            preCheckAndInit();

            //收集版本信息
            Map<String, Map<String, Object>> moduleVersionInfo = analysisModule(libPath);

            //打印模块版本信息
            printVersionInfo(moduleVersionInfo);

            List<String> libList = loadLib(libPath);

            while (true) {
                printHelp(libList);
                //处理键盘输入
                String inputJar = sc.nextLine();
                List<String> jarNumberList = new ArrayList();
                if(StringUtils.isNumeric(inputJar)){
                    if(Integer.parseInt(inputJar) < libList.size()){
                        if(inputJar.equals("0")){
                            for(int i =1 ; i < libList.size(); i++){
                                jarNumberList.add(i + "");
                            }
                        }else{
                            jarNumberList.add(inputJar);
                        }

                    }else{
                        System.err.println("please input right number");
                        continue;
                    }
                }else if (inputJar.contains(",")){
                    String [] numberArray = inputJar.split(",");
                    for(String number : numberArray){
                        if(StringUtils.isNumeric(inputJar) && Integer.parseInt(inputJar) < libList.size() && !number.equals("0")) {
                            jarNumberList = Arrays.asList(numberArray);
                        }else{
                            System.err.println("please input right number");
                        }
                    }
                }else if(inputJar.equals("q")){
                    return;
                }else{
                    System.err.println("please check you input ");
                    continue;
                }

                //升级模块
                for(int i = 0; i< jarNumberList.size(); i++ ){
                    String numberIndex = jarNumberList.get(i);
                    String jarName = libList.get(Integer.parseInt(numberIndex));
                    System.out.println("start upgrade lib " + jarName);
                    Map<String, Object> moduleInfo = moduleVersionInfo.get(jarName);
                    upgradeModule(jarName);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.err.println("thanks all");
            sc.close();
        }

        con.close();
    }

    private static void preCheckAndInit() throws Exception{
        System.out.println("get environment variables $TECHSURE_HOME...");
        techsureHome = System.getenv("TECHSURE_HOME");
        System.out.println(" TECHSURE_HOME is " + techsureHome);

        if (StringUtils.isBlank(techsureHome)) {
            System.out.println("can not get techsure_home from environment variables, please input  $TECHSURE_HOME , or use default /app");
            techsureHome = sc.nextLine();
        }

        if (StringUtils.isBlank(techsureHome)) {
            techsureHome = "/app";
            System.out.println("$TECHSURE_HOME is not define in environment variables, use default value : /app");
        }

        System.out.println("$TECHSURE_HOME is " + techsureHome);

        configPath = techsureHome + "/systems/" + instanceName +"/config/config.properties";
//        configPath = "D:\\fand\\software\\IDEA_CODE\\balantflow-webroot\\config\\config.properties";
        System.out.println("load config with config file:" + configPath);
        loadConfig(configPath);

        checkAuditTableExists();

        System.out.println("load sql runner...");
        loadRunner();

        libPath = techsureHome + "/systems/" + instanceName + "/apps/balantflow/WEB-INF/lib/";
//        libPath = "D:\\fand\\software\\IDEA_CODE\\balantflow-webroot\\target\\balantflow\\WEB-INF\\lib";
        System.out.println("lib path is " + libPath);

    }


    private static List<String> getLibVersionList(JarFile jarFile) throws IOException {
        String filePath = "META-INF/dbscript-";
        List<String> versionList = new ArrayList<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        List<String> versionStringList = new ArrayList<>();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String dbScriptDir = jarEntry.getName();
            if (dbScriptDir.startsWith(filePath)) {
                versionStringList.add(jarEntry.getName());
            }
        }

        for (String versionStr : versionStringList) {
            String[] versionArray = versionStr.split("/");
            int length = versionArray.length;
            if (length == 3 && !versionStr.contains("version")) {
                String version = versionArray[length - 1];
                versionList.add(version);
            }

        }
        versionList.sort(String::compareTo);
        return versionList;
    }



    private static Map<String, Map<String, Object>> analysisModule(String libPath){
        Map<String, Map<String, Object>> moduleInfo = new HashMap<>();
        List<String> libList = loadLib(libPath);

        for (String libName : libList) {
            Map<String, Object> libVersionInfo = new HashMap<>();
            List<String> allVersionList = new ArrayList<>();
            List<String> upgradeVersionList = new ArrayList<>();
            try {
                JarFile jarFile = new JarFile(new File(libPath + File.separator + libName));

                allVersionList = getLibVersionList(jarFile);
                String uniqueName = getUniqueName(libName);
                LibVo dbLib = getLibVersion(uniqueName);
                int currentVersionIndex = 0;
                if (dbLib == null || StringUtils.isBlank(dbLib.getVersion())){
                    updateLibVersion(uniqueName, "", "failed");
                    System.err.println(libName + " not define current version ");
                } else {
                    System.out.println("current version is " + dbLib.getVersion());
                    currentVersionIndex = allVersionList.indexOf(dbLib.getVersion());
                }

                if(dbLib == null || StringUtils.isBlank(dbLib.getVersion()) || currentVersionIndex == -1){
                    libVersionInfo.put("defineCurrent", false);
                    libVersionInfo.put("currentVersion", "");
                    System.err.println("undefine current version in jar or can not find current version ");
//                    for (int i = 0; i < allVersionList.size(); i++) {
//                        if (i > currentVersionIndex || (i == currentVersionIndex && dbLib.getStatus().equals("failed"))) {
//                            upgradeVersionList.add(allVersionList.get(i));
//                        }
//                    }
                }else{
                    libVersionInfo.put("defineCurrent", true);
                    libVersionInfo.put("currentVersion", dbLib.getVersion());
                    for (int i = 0; i < allVersionList.size(); i++) {
                        if (i > currentVersionIndex || (i == currentVersionIndex && dbLib.getStatus().equals("failed"))) {
                            upgradeVersionList.add(allVersionList.get(i));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println(libName + " ");
            }
            libVersionInfo.put("allVersionList", allVersionList);
            libVersionInfo.put("upgradeVersionList", upgradeVersionList);
            moduleInfo.put(libName, libVersionInfo);
        }

        return moduleInfo;
    }

    private static void printVersionInfo(Map<String, Map<String, Object>> moduleVersionInfo){
        System.out.println("===================================================================================================");
        System.out.println("===================================================================================================");
        Iterator<Map.Entry<String, Map<String, Object>>> entries = moduleVersionInfo.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Map<String, Object>> entry = entries.next();
            String jarName = entry.getKey();
            Map<String, Object> libInfo = entry.getValue();
            StringBuilder msgBuilder = new StringBuilder();
            boolean defineCurrent  = (boolean) libInfo.getOrDefault("defineCurrent", false);
            String currentVersion = (String) libInfo.getOrDefault("currentVersion", "");

            msgBuilder.append("jar:" + jarName);
            msgBuilder.append(defineCurrent ? "current version is " + currentVersion : "not define current version, show all version");
            System.out.println(msgBuilder.toString());

            List<String> allVersionList = (List<String>)libInfo.getOrDefault("allVersionList", new ArrayList<>());
            List<String> upgradeVersionList = (List<String>)libInfo.getOrDefault("upgradeVersionList", new ArrayList<>());

            for(int i = 0 ; i < allVersionList.size(); i++){
                System.out.print(allVersionList.get(i) + " >>> ");
            }
            System.out.println("");

        }
        System.out.println("===================================================================================================");
        System.out.println("===================================================================================================");
    }

    private static void printHelp(List<String> libList){
        System.out.println("===================================================================================================");
        for(int i = 0; i < libList.size(); i++){
            System.out.println( i + " " + libList.get(i));
        }
        System.out.println("please input number to upgrade, multiple split with ',' or input 0 to upgrade all , input q to exit");
        System.out.println("===================================================================================================");
    }

    private static String readJarContent(JarFile jarFile, String path, boolean verbose) throws IOException {
        BufferedReader reader = new BufferedReader(new UnicodeReader(jarFile.getInputStream(jarFile.getEntry(path)), null));
        StringBuilder buffer = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if(verbose){
                System.out.println(line);
            }
            buffer.append(line);
        }
        return buffer.toString();
    }

    private void writeJarContent(JarFile jarFile, JarEntry jarEntry, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(jarFile.getName(), true);
        try (JarOutputStream jos = new JarOutputStream(fos)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry oldEntry = entries.nextElement();
                if (jarEntry.getName().equals(oldEntry.getName())) {
                    jos.putNextEntry(new JarEntry(jarEntry.getName()));
                    jos.write(content.getBytes());
                } else {
                    jos.putNextEntry(new JarEntry(jarEntry.getName()));
                    jos.write(streamToByte(jarFile.getInputStream(oldEntry)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] streamToByte(InputStream inputStream) {
        ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outSteam.write(buffer, 0, len);
            }
            outSteam.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outSteam.toByteArray();
    }

    private static void loadRunner() throws Exception {
        Class.forName(driverName);
        con = DriverManager.getConnection(url, username, password);
        scriptExecutor = new ScriptRunner(con);
        scriptExecutor.setFullLineDelimiter(false);
        scriptExecutor.setDelimiter(";");
        scriptExecutor.setSendFullScript(false);
        scriptExecutor.setAutoCommit(true);
        scriptExecutor.setStopOnError(true);
    }

    private static LibVo getLibVersion(String lib) {
        LibVo libVo = null;
        try {
            open();
            PreparedStatement st = dbConnection.prepareStatement(DatabaseUpgradeConstants.GET_DB_BASELINE);
            st.setString(1, lib);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                libVo = new LibVo();
                libVo.setName(rs.getString("jar"));
                libVo.setStatus(rs.getString("status"));
                libVo.setVersion(rs.getString("version"));
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(lib + " get version failed, " + e.getMessage());
        } finally {
            close();
        }
        return libVo;
    }


    private static void checkAuditTableExists() {
        try {
            open();
            PreparedStatement st = dbConnection.prepareStatement(DatabaseUpgradeConstants.CHECK_VERSION_TABLE);
            st.executeUpdate();
            st = dbConnection.prepareStatement(DatabaseUpgradeConstants.CHECK_AUDIT_TABLE);
            st.executeUpdate();
            st.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private static void updateLibVersion(String lib, String version, String status) {
        try {
            open();
            PreparedStatement st = dbConnection.prepareStatement(DatabaseUpgradeConstants.UPDATE_DB_BASELINE);
            st.setString(1, lib);
            st.setString(2, version);
            st.setString(3, status);

            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(lib + " get version failed, " + e.getMessage());
        } finally {
            close();
        }
    }


    private static String getSqlStatus(String lib, String version, String sqlName, String type) {
        String status = null;
        try {
            open();
            PreparedStatement st = dbConnection.prepareStatement(DatabaseUpgradeConstants.CHECK_SQL_STATUS);
            st.setString(1, lib);
            st.setString(2, version);
            st.setString(3, sqlName);
            st.setString(4, type);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                status = rs.getString("status");
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(lib + " get version failed, " + e.getMessage());
        } finally {
            close();
        }
        return status;
    }


    private static void updateSqlStatus(String lib, String version, String sqlName, String type, String status) {
        try {
            open();
            PreparedStatement st = dbConnection.prepareStatement(DatabaseUpgradeConstants.UPDATE_SQL_STATUS);
            st.setString(1, lib);
            st.setString(2, version);
            st.setString(3, sqlName);
            st.setString(4, type);
            st.setString(5, status);
            st.executeUpdate();
            st.close();
        } catch (SQLException e) {
            throw new RuntimeException(lib + " get version failed, " + e.getMessage());
        } finally {
            close();
        }
    }


    private static String executeSql(ScriptRunner scriptExecutor, JarFile jarFile, String sql) throws Exception {
        System.out.println("exec sql " + sql);

        System.out.println("**********************************************************************************************************************");
        System.out.println("**********************************************************************************************************************");
        readJarContent(jarFile, sql , true);
        System.out.println("**********************************************************************************************************************");
        System.out.println("**********************************************************************************************************************");

        while (true) {
            System.err.println("please input 'y' to execute , 'n' to skip  or 'q' to exit");
            String inputJar = sc.nextLine();
            if("y".equals(inputJar)){
                BufferedReader sqlReader = new BufferedReader(new UnicodeReader(jarFile.getInputStream(jarFile.getEntry(sql)), null));
                scriptExecutor.runScript(sqlReader);
                return DatabaseUpgradeConstants.STATUS_SUCCEED;
            }else if ("n".equals(inputJar)){
                return DatabaseUpgradeConstants.STATUS_SKIP;
            }else if ("q".equals(inputJar)){
                return DatabaseUpgradeConstants.STATUS_INTERRUPT;
            }
        }


    }

    private static void close() {
        if (dbConnection == null) {
            return;
        }
        try {
            dbConnection.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        } finally {
            dbConnection = null;
        }
    }

    private static void loadConfig(String configPath) throws IOException {
        Properties pro = new Properties();
        InputStream is = null;
        try {
            is = new FileInputStream(new File(configPath));
            pro.load(is);
        } catch (Exception ex) {
            System.out.println("read config failed，" + ex.getMessage());
            throw ex;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        driverName = pro.getProperty("db.driverClassName", "com.mysql.jdbc.Driver");
        url = pro.getProperty("db.url", "jdbc:mysql://localhost:3306/bsm?characterEncoding=UTF-8");
        username = pro.getProperty("db.username", "root");
        password = pro.getProperty("db.password", "zanyue$2012");
    }

    private static void open() throws SQLException {
        if (dbConnection != null) {
            return;
        }

        if (driver == null) {
            try {
                Class<?> clazz = Class.forName(driverName);
                driver = ((Driver) clazz.newInstance());
            } catch (Throwable e) {
                throw new SQLException(e.getMessage(), e);
            }
        }

        Properties props = new Properties();
        if (username != null) {
            props.put("user", username);
        }
        if (password != null) {
            props.put("password", password);
        }
        dbConnection = driver.connect(url, props);
        dbConnection.setAutoCommit(true);
        if (dbConnection == null) {
            throw new SQLException("初始化数据库连接失败，请检查配置文件或数据库是否可以连接");
        }
    }

    private static List<String> loadLib(String libPath) {
        File libDir = new File(libPath);
        List<String> libList = new ArrayList<>();
        libList.add("all");
        if (libDir.isDirectory()) {
            String[] lib = libDir.list();
            if(lib != null && lib.length > 0 ){
                for (String libJar : lib) {
                    if (libJar.contains("balantflow")) {
                        libList.add(libJar);
                    }
                }
            }

        }
        return libList;
    }

    private static String getUniqueName(String libName) {
        String[] nameArray = libName.split("-");
        String uniqueName = "";
        int length = nameArray.length;
        if (length == 1) {
            uniqueName = libName;
        } else {
            for (int i = 0; i < length - 1; i++) {
                uniqueName = uniqueName + nameArray[i] + "-";
            }
        }

        return uniqueName;
    }



    private static String upgradeModule(String libName) throws Exception {
        System.out.println("start upgrade " + libName);

        JarFile jarFile = new JarFile(new File(libPath + File.separator + libName));

        String filePath = "META-INF/dbscript-";
        Enumeration<JarEntry> entries = jarFile.entries();
        List<String> versionStringList = new ArrayList<>();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String dbScriptDir = jarEntry.getName();
            if (dbScriptDir.startsWith(filePath)) {
                versionStringList.add(jarEntry.getName());
            }
        }

        List<String> versionList = new ArrayList<>();
        for (String versionStr : versionStringList) {
            String[] versionArray = versionStr.split("/");
            int length = versionArray.length;
            if (length == 3 && !versionStr.contains("version")) {
                String version = versionArray[length - 1];
                versionList.add(version);
            }

        }
        versionList.sort(String::compareTo);


        Map<String, Object> moduleVersionMap = new TreeMap<>(Comparator.naturalOrder());

        System.out.println("collect ddl and dml...");




        versionList.forEach(version -> {
            Map<String, List<String>> versionSql = new HashMap<>();
            List<String> ddlList = new ArrayList<>();
            List<String> dmlList = new ArrayList<>();
            versionStringList.stream().filter(a -> a.split("/").length == 5 && a.contains(version) && a.contains("ddl")).forEach(ddlList::add);
            versionStringList.stream().filter(a -> a.split("/").length == 5 && a.contains(version) && a.contains("dml")).forEach(dmlList::add);
            ddlList.sort(String::compareTo);
            dmlList.sort(String::compareTo);
            versionSql.put("ddl", ddlList);
            versionSql.put("dml", dmlList);
            moduleVersionMap.put(version, versionSql);
        });

        System.out.println("get module baseline from db table: flow_db_upgrade_version");

        String uniqueName = getUniqueName(libName);
        LibVo dbLib = getLibVersion(uniqueName);
        int currentVersionIndex;
        if (dbLib == null || StringUtils.isBlank(dbLib.getVersion())) {

            while (true) {
                System.err.println("not define current version , only upgrade lasest version? ");
                System.err.println("please input 'y' to upgrade the latest version(only) , input 'n' to skip , input 'q' to exit ");
                String inputJar = sc.nextLine();
                if("y".equals(inputJar)){
                    currentVersionIndex = versionList.size() == 0 ? 0 : versionList.size() -1;
                    break;
                }else if ("n".equals(inputJar)){
                    return DatabaseUpgradeConstants.STATUS_SKIP;
                }else if ("q".equals(inputJar)){
                    return DatabaseUpgradeConstants.STATUS_INTERRUPT;
                }else{
                    System.err.println("please check your input ");
                }
            }
        }else{
            currentVersionIndex = versionList.indexOf(dbLib.getVersion());
        }


        boolean moduleUpgradeStatus = true;

        for (int i = 0; i < versionList.size(); i++) {
            if (i > currentVersionIndex || (i == currentVersionIndex && StringUtils.equals(DatabaseUpgradeConstants.STATUS_FAILED, dbLib.getStatus()))) {
                String nextVersion = versionList.get(i);
                try {
                    updateLibVersion(uniqueName, nextVersion, DatabaseUpgradeConstants.STATUS_FAILED);

                    System.out.println("upgrade to version  " + nextVersion);
                    Map<String, List<String>> scriptList = (Map<String, List<String>>) moduleVersionMap.get(nextVersion);

                    System.out.println("run ddl...");
                    List<String> ddlList = scriptList.get("ddl");
                    for (String ddl : ddlList) {
                        int dmlIndex = ddl.split("/").length - 1;
                        String shortPath = ddl.split("/")[dmlIndex];

                        try {
                            //避免执行成功的sql重复执行
                            String sqlStatus = getSqlStatus(libName, nextVersion, shortPath, "ddl");
                            if (!StringUtils.equals(sqlStatus, DatabaseUpgradeConstants.STATUS_SUCCEED)) {
                                String sqlExecStatus = executeSql(scriptExecutor, jarFile, ddl);
                                updateSqlStatus(libName, nextVersion, shortPath, "ddl", sqlExecStatus);
                            }
                        } catch (Exception e) {
                            moduleUpgradeStatus = false;
                            updateSqlStatus(libName, nextVersion, shortPath, "ddl", DatabaseUpgradeConstants.STATUS_FAILED);
                        }

                    }

                    System.out.println("run dml...");
                    List<String> dmlList = scriptList.get("dml");
                    for (String dml : dmlList) {
                        int dmlIndex = dml.split("/").length - 1;
                        String shortPath = dml.split("/")[dmlIndex];
                        try {
                            //避免执行成功的sql重复执行
                            String sqlStatus = getSqlStatus(libName, nextVersion, shortPath, "dml");
                            if (!StringUtils.equals(sqlStatus, "succeed")) {
                                executeSql(scriptExecutor, jarFile, dml);
                                updateSqlStatus(libName, nextVersion, shortPath, "dml", DatabaseUpgradeConstants.STATUS_SUCCEED);
                            }
                        } catch (Exception e) {
                            moduleUpgradeStatus = false;
                            updateSqlStatus(libName, nextVersion, shortPath, "dml", DatabaseUpgradeConstants.STATUS_FAILED);
                        }
                    }
                } catch (Exception e) {
                    moduleUpgradeStatus = false;
                }
                updateLibVersion(uniqueName, nextVersion, moduleUpgradeStatus ? DatabaseUpgradeConstants.STATUS_SUCCEED : DatabaseUpgradeConstants.STATUS_FAILED);
            }
        }


        System.out.println(libName + " upgrade end ");
        return DatabaseUpgradeConstants.STATUS_SUCCEED;
    }


    static class LibVo {

        private String name;
        private String status;
        private String error;
        private String version;

        public String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        String getStatus() {
            return status;
        }

        void setStatus(String status) {
            this.status = status;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        String getVersion() {
            return version;
        }

        void setVersion(String version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return name + " " + version;
        }
    }
}
