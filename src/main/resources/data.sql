-- ADM Content Service default data
-- Derived from adm-content-service sql.properties (mysql.cm_insertDefaults)

INSERT IGNORE INTO `idtype` (`name`) VALUES ('onecms');
INSERT IGNORE INTO `idtype` (`name`) VALUES ('draft');

INSERT IGNORE INTO `attributes` (`name`) VALUES ('deleted');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('created_at');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('created_by');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('insertParentId');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('securityParentId');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('objectType');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('inputTemplate');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('partition');

INSERT IGNORE INTO `views` (`name`, `created_by`) VALUES ('p.latest', '98');
INSERT IGNORE INTO `views` (`name`, `created_by`) VALUES ('p.public', '98');
INSERT IGNORE INTO `views` (`name`, `created_by`) VALUES ('p.deleted', '98');

INSERT IGNORE INTO `aliases` (`name`, `created_by`) VALUES ('externalId', '98');

INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('CREATE');
INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('UPDATE');
INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('REMOVE');
INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('ASSIGN_VIEW');
INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('REMOVE_VIEW');
INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('SCHEDULED_PURGE_VERSION');
INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('PURGE_VERSION');

INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('DELETE');

INSERT IGNORE INTO `attributes` (`name`) VALUES ('modifier');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('modificationTime');
INSERT IGNORE INTO `attributes` (`name`) VALUES ('creationTime');

-- Default user (OLDSHA hash of 'sysadmin' — matches Polopoly default)
INSERT IGNORE INTO `registeredusers` (`loginname`, `passwordhash`, `regtime`, `active`)
VALUES ('sysadmin', 'bb40d977a94b02f2', 0, 1);
