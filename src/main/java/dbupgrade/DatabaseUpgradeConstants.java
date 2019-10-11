package dbupgrade;

import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.jdbc.ScriptRunner;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class DatabaseUpgradeConstants {

    public static String STATUS_SKIP = "skip";

    public static String STATUS_FAILED = "failed";

    public static String STATUS_SUCCEED = "succeed";

    public static String STATUS_INTERRUPT = "interrupt";

    static String GET_DB_BASELINE = "SELECT jar, version, status FROM flow_db_upgrade_version WHERE jar = ? ";

    static String UPDATE_DB_BASELINE = "REPLACE INTO flow_db_upgrade_version (jar, version, status ) values ( ?, ?, ? ) ";

    static String CHECK_SQL_STATUS = "SELECT `status` FROM `flow_db_upgrade_audit` WHERE jar = ? AND `version` = ?  AND `sql_name` = ? AND `type` = ?";

    static String UPDATE_SQL_STATUS = "REPLACE INTO flow_db_upgrade_audit (jar, version, sql_name, type, status ) values ( ?, ?, ?, ?, ? )";

    static String CHECK_AUDIT_TABLE = "CREATE TABLE IF NOT EXISTS `flow_db_upgrade_audit` (`jar` varchar(50) DEFAULT NULL,`sql_name` varchar(50) DEFAULT NULL, `version` varchar(50) DEFAULT NULL, `type` varchar(50) DEFAULT NULL, `status` varchar(50) DEFAULT NULL,UNIQUE KEY `idx_sql` (`jar`,`sql_name`,`version`)) ENGINE=InnoDB DEFAULT CHARSET=utf8";

    static String CHECK_VERSION_TABLE = "CREATE TABLE IF NOT EXISTS `flow_db_upgrade_version` ( `jar` varchar(50) NOT NULL COMMENT 'jar包', `version` varchar(50) NOT NULL COMMENT '版本', `status` varchar(50) NOT NULL COMMENT '状态', PRIMARY KEY (`jar`)) ENGINE=InnoDB DEFAULT CHARSET=utf8";

}
