package net.welights.tools.dbupgrade;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;

import net.welights.tools.dbupgrade.core.UpgradeProcess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author welights
 */
public class DatabaseUpgrade {

    @Parameter
    private List<String> parameters = Lists.newArrayList();

    @Parameter(names = {"-h"}, description = "db host", order = 0)
    private String host = "127.0.0.0:3306";

    @Parameter(names = {"-u", "-username"}, description = "db user", order = 1)
    private String username = "admin";

    @Parameter(names = {"-p", "-password"}, description = "db password", password = true, order = 2)
    private String password = "123456";

    @Parameter(names = {"-d", "-database"}, description = "db schema", required = true, order = 3)
    private String database;

    @Parameter(names = {"--version"}, help = true, order = 4)
    private boolean version;

    @Parameter(names = {"--help"}, help = true, order = 5)
    private boolean help;

    @DynamicParameter(names = "-D", description = "Dynamic parameters go here")
    private Map<String, String> dynamicParams = new HashMap<>();

    public static void main(String[] args) throws Exception {
        DatabaseUpgrade cmd = new DatabaseUpgrade();
        JCommander jCommander = JCommander.newBuilder().addObject(cmd).build();
        jCommander.parse(args);
        cmd.execute(jCommander);
    }

    private void execute(JCommander jCommander) throws Exception {
        if (help) {
            jCommander.setProgramName("java -jar schema-upgrade.jar -h 127.0.0.1 -u root -p passs  -d test /sqlpath");
            jCommander.usage();
            return;
        }

        if (version) {
            JCommander.getConsole().println("release version: 1.0.0");
            return;
        }

        new UpgradeProcess(this).processUpgrade();
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isVersion() {
        return version;
    }

    public void setVersion(boolean version) {
        this.version = version;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public Map<String, String> getDynamicParams() {
        return dynamicParams;
    }

    public void setDynamicParams(Map<String, String> dynamicParams) {
        this.dynamicParams = dynamicParams;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}
