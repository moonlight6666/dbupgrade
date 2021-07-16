package net.welights.tools.dbupgrade.common;

/**
 * @author welights
 */
public class Constants {

    public static final String STATUS_SKIP = "skip";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SUCCEED = "success";
    public static final String STATUS_INTERRUPT = "interrupt";
    public static final String GET_DB_BASELINE = "SELECT module, version, status FROM db_upgrade_version WHERE module = ? ";
    public static final String UPDATE_DB_BASELINE = "REPLACE INTO db_upgrade_version (module, version, status ) values ( ?, ?, ? ) ";
    public static final String CHECK_SQL_STATUS = "SELECT `module_name`, `script_name`, `module_version`, `type`,  `status` , `message` FROM `db_upgrade_audit` WHERE module_name = ? AND `module_version` = ?  AND `script_name` = ? AND `type` = ?";
    public static final String UPDATE_SQL_STATUS = "REPLACE INTO db_upgrade_audit (module_name, module_version, script_name, type, status , message) values ( ?, ?, ?, ?, ?, ? )";
    public static final String CHECK_AUDIT_TABLE = "CREATE TABLE IF NOT EXISTS `db_upgrade_audit` (`module_name` varchar(50) DEFAULT NULL, `module_version` varchar(50) DEFAULT NULL, `type` varchar(50) DEFAULT NULL,`script_name` varchar(50) DEFAULT NULL, `status` varchar(50) DEFAULT NULL, `message` text, UNIQUE KEY `idx_sql` (`module_name`,`script_name`,`module_version`, `type`)) ENGINE=InnoDB DEFAULT CHARSET=utf8";
    public static final String CHECK_VERSION_TABLE = "CREATE TABLE IF NOT EXISTS `db_upgrade_version` ( `module` varchar(50) NOT NULL COMMENT '模块', `version` varchar(50) NOT NULL COMMENT '版本', `status` varchar(50) NOT NULL COMMENT '状态', PRIMARY KEY (`module`)) ENGINE=InnoDB DEFAULT CHARSET=utf8";

    private Constants() {
        throw new IllegalStateException("Utility class");
    }

}
