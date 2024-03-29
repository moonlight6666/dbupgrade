[![Build](https://github.com/logerror/dbupgrade/actions/workflows/maven.yml/badge.svg?branch=release&event=push)](https://github.com/logerror/dbupgrade/actions/workflows/maven.yml)

## 设计

按照规范(符合semver规范的版本声明)存放变更脚本，通过在数据库中新建两张表来维护版本信息   
//TODO: 改用其他的方式记录版本信息

## 使用

![image](https://user-images.githubusercontent.com/13896386/126306911-ac5aca90-c552-4f43-97b2-5cd707d1158e.png)

构建可执行文件

```
mvn clean install
```

查看帮助信息

```
java -jar schema-upgrade.jar --help
```

进入target目录执行以下命令

```
java -jar schema-upgrade.jar -h 127.0.0.1 -u root -p passs  -d test /sqlpath
```

## 规范

1. 统一将项目sql用版本管理，结构如下：版本>ddl(dml)>sql文件(按执行顺序排列))        
   注释可以用--或//开头，只支持单行注释

```
   |--sql-path    
   |------module-flow    
   |--------1.0.0（符合semver标准的版本声明）   
   |-------------DDL    
   |-----------------001.sql   
   |-----------------002.sql    
   |-------------DML   
   |-----------------001.sql   
   |-----------------002.sql   
   |--------2.0.0 (符合semver标准的版本声明）   
   |-------------DDL    
   |-----------------001.sql   
   |-----------------002.sql    
   |-------------DML   
   |-----------------001.sql   
   |-----------------002.sql
```

2. 支持的脚本

DDL语句
```
-- alter table test_xx
ALTER TABLE `test_xx` ADD COLUMN `xx` double   NULL DEFAULT 0 COMMENT 'xxxx' after `xxxx` ;
```

DML语句
```
insert  into`qrtz_lock`(`SCHED_NAME`,`LOCK_NAME`) values ('schedulerFactory','STATE_ACCESS'),('schedulerFactory','TRIGGER_ACCESS'); 
```

存储过程
```
DELIMITER $$

USE `bsm`$$

DROP PROCEDURE IF EXISTS `javapc`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `javapc`(IN myid VARCHAR(64))
MAIN_BLOCK:  BEGIN
DECLARE wish_id VARCHAR(64);
DECLARE wish_memberid VARCHAR(64);
DECLARE wish_goodsid VARCHAR(64);
DECLARE wish_addtime VARCHAR(64);
DECLARE  result INT DEFAULT 0;
IF(EXISTS(SELECT * FROM tb_wish WHERE id=myid)) THEN
SET result=0;
SELECT id,memberid,goods_id,ADDTIME INTO wish_id,wish_memberid,wish_goodsid,wish_addtime FROM tb_wish WHERE id=myid  ;  
ELSE
SET result=0; 
END IF; 
SELECT wish_id,wish_memberid,wish_goodsid,wish_addtime;
END MAIN_BLOCK$$
DELIMITER ;  
```
