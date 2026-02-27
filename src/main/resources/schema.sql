-- ADM Content Service MySQL schema
-- Derived from adm-content-service sql.properties (mysql.cm_createTables)

CREATE TABLE IF NOT EXISTS `idtype` (
    `id`         INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `name`       VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    UNIQUE KEY `idtype_name_UNIQUE` (`name`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `attributes` (
    `attrid`     INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `name`       VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    UNIQUE KEY `attributes_name_UNIQUE` (`name`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `id` (
    `id`         VARCHAR(255) PRIMARY KEY NOT NULL,
    `idtype`     INTEGER NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    KEY `id_unique` (`idtype`, `id`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `idversions` (
    `versionid`  INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `idtype`     INTEGER NOT NULL,
    `id`         VARCHAR(255) NOT NULL,
    `version`    VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    UNIQUE KEY `idversions_version_UNIQUE` (`idtype`, `id`, `version`),
    KEY `idversions_id` (`idtype`, `id`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `idattributes` (
    `id`          VARCHAR(255) NOT NULL,
    `attrid`      INTEGER NOT NULL,
    `str_value`   VARCHAR(255) NOT NULL,
    `int_value`   INTEGER NOT NULL,
    `created_at`  TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by`  VARCHAR(255) NOT NULL,
    `modified_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `modified_by` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`id`, `attrid`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `views` (
    `viewid`     INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `name`       VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    UNIQUE KEY `views_name_UNIQUE` (`name`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `idviews` (
    `versionid`  INTEGER NOT NULL,
    `viewid`     INTEGER NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`versionid`, `viewid`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `aliases` (
    `aliasid`    INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `name`       VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    UNIQUE KEY `aliases_name_UNIQUE` (`name`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `idaliases` (
    `idtype`     INTEGER NOT NULL,
    `id`         VARCHAR(255) NOT NULL,
    `aliasid`    INTEGER NOT NULL,
    `value`      VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`idtype`, `id`, `aliasid`),
    UNIQUE KEY `idaliases_value_UNIQUE` (`aliasid`, `value`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `contents` (
    `contentid`   INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `versionid`   INTEGER NOT NULL,
    `contenttype` VARCHAR(255) NOT NULL,
    `created_at`  TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by`  VARCHAR(255) NOT NULL,
    `modified_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `modified_by` VARCHAR(255) NOT NULL,
    UNIQUE KEY `contents_UNIQUE` (`contentid`, `versionid`),
    KEY `contents_versionid` (`versionid`),
    KEY `contents_contenttype` (`contenttype`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `aspects` (
    `aspectid`   INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `versionid`  INTEGER NOT NULL,
    `contentid`  VARCHAR(255) NOT NULL,
    `name`       VARCHAR(255) NOT NULL,
    `data`       JSON NOT NULL,
    `md5`        CHAR(32) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    UNIQUE KEY `aspects_UNIQUE` (`aspectid`, `versionid`),
    UNIQUE KEY `aspects_contentid_UNIQUE` (`contentid`),
    KEY `aspects_versionid` (`versionid`),
    KEY `aspects_name` (`name`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `aspectslocations` (
    `contentid` INTEGER NOT NULL,
    `aspectid`  INTEGER NOT NULL,
    PRIMARY KEY (`contentid`, `aspectid`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `eventtypes` (
    `eventid`    INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `name`       VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    UNIQUE KEY `eventtypes_name_UNIQUE` (`name`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `eventsqueue` (
    `id`         INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `eventtype`  INTEGER NOT NULL,
    `versionid`  INTEGER NOT NULL,
    `viewid`     INTEGER,
    `created_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by` VARCHAR(255) NOT NULL,
    KEY `eventsqueue_versionid` (`versionid`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `changelist` (
    `id`          INTEGER PRIMARY KEY NOT NULL,
    `eventtype`   INTEGER NOT NULL,
    `idtype`      INTEGER NOT NULL,
    `contentid`   VARCHAR(255) NOT NULL,
    `version`     VARCHAR(255) NOT NULL,
    `contenttype` VARCHAR(255) NOT NULL,
    `created_at`  TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `created_by`  VARCHAR(255) NOT NULL,
    `modified_at` TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    `modified_by` VARCHAR(255) NOT NULL,
    `commit_at`   TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    UNIQUE KEY `changelist_UNIQUE` (`idtype`, `contentid`, `version`),
    UNIQUE KEY `changelist_contentid_UNIQUE` (`contentid`),
    KEY `changelist_contenttype` (`contenttype`),
    KEY `changelist_eventtype` (`eventtype`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `changelistattributes` (
    `id`        INTEGER NOT NULL,
    `attrid`    INTEGER NOT NULL,
    `str_value` VARCHAR(255),
    `num_value` INTEGER,
    PRIMARY KEY (`id`, `attrid`),
    KEY `changelistattributes_str_value` (`str_value`),
    KEY `changelistattributes_num_value` (`num_value`)
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS `users` (
    `userid`        INTEGER AUTO_INCREMENT PRIMARY KEY NOT NULL,
    `username`      VARCHAR(255) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_at`    TIMESTAMP(3) DEFAULT NOW(3) NOT NULL,
    UNIQUE KEY `users_username_UNIQUE` (`username`)
) ENGINE = INNODB;
