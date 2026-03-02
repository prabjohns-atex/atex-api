package com.atex.desk.api.migration;

import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desk-api schema additions on top of the legacy ADM/Polopoly baseline:
 * <ol>
 *   <li>Add principalid column to registeredusers (if not already present)</li>
 *   <li>Create indexer_state table (if not already present)</li>
 *   <li>Seed default data for sysadmin principalid and live indexer</li>
 *   <li>Backfill principalid from legacy Polopoly tables (if they exist)</li>
 * </ol>
 * All steps use IF NOT EXISTS / conditional checks so this is safe on both
 * fresh databases and existing ones where some changes were already applied.
 */
public class V2__DeskApiUpgrade extends BaseJavaMigration
{
    private static final Logger log = LoggerFactory.getLogger(V2__DeskApiUpgrade.class);

    @Override
    public void migrate(Context context) throws Exception
    {
        try (Statement stmt = context.getConnection().createStatement())
        {
            addPrincipalIdColumn(stmt);
            createIndexerStateTable(stmt);
            seedDefaultData(stmt);
            backfillPrincipalIds(stmt);
        }
    }

    private void addPrincipalIdColumn(Statement stmt) throws Exception
    {
        if (!columnExists(stmt, "registeredusers", "principalid"))
        {
            stmt.executeUpdate(
                "ALTER TABLE `registeredusers` " +
                "ADD COLUMN `principalid` VARCHAR(64) DEFAULT NULL");
            stmt.executeUpdate(
                "ALTER TABLE `registeredusers` " +
                "ADD KEY `registeredusers_principalid_IDX` (`principalid`)");
            log.info("Added principalid column to registeredusers");
        }
    }

    private void createIndexerStateTable(Statement stmt) throws Exception
    {
        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS `indexer_state` (" +
            "    `indexer_id`      VARCHAR(64) PRIMARY KEY NOT NULL," +
            "    `job_type`        VARCHAR(32) NOT NULL," +
            "    `status`          VARCHAR(16) NOT NULL DEFAULT 'REQUESTED'," +
            "    `last_cursor`     BIGINT NOT NULL DEFAULT 0," +
            "    `config`          JSON," +
            "    `total_items`     BIGINT," +
            "    `processed_items` BIGINT NOT NULL DEFAULT 0," +
            "    `error_count`     INT NOT NULL DEFAULT 0," +
            "    `error_message`   TEXT," +
            "    `locked_by`       VARCHAR(255)," +
            "    `locked_at`       TIMESTAMP(3)," +
            "    `started_at`      TIMESTAMP(3)," +
            "    `created_at`      TIMESTAMP(3) DEFAULT NOW(3) NOT NULL," +
            "    `updated_at`      TIMESTAMP(3) DEFAULT NOW(3) NOT NULL," +
            "    KEY `indexer_state_job_type` (`job_type`)," +
            "    KEY `indexer_state_status` (`status`)" +
            ") ENGINE = INNODB");
    }

    private void seedDefaultData(Statement stmt) throws Exception
    {
        // Set principalid for sysadmin if still null
        stmt.executeUpdate(
            "UPDATE `registeredusers` SET `principalid` = '98' " +
            "WHERE `loginname` = 'sysadmin' AND `principalid` IS NULL");

        // DELETE event type (desk-api addition for soft-delete support)
        stmt.executeUpdate(
            "INSERT IGNORE INTO `eventtypes` (`name`) VALUES ('DELETE')");

        // policyId alias namespace (desk-api addition for legacy content migration)
        stmt.executeUpdate(
            "INSERT IGNORE INTO `aliases` (`name`, `created_by`) VALUES ('policyId', '98')");

        // Live Solr indexer state
        stmt.executeUpdate(
            "INSERT IGNORE INTO `indexer_state` (`indexer_id`, `job_type`, `status`, `last_cursor`) " +
            "VALUES ('solr', 'LIVE', 'RUNNING', 0)");
    }

    private void backfillPrincipalIds(Statement stmt)
    {
        try
        {
            // Check if legacy Polopoly tables exist
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('component', 'attributeid', 'externalids')");
            rs.next();
            int tableCount = rs.getInt(1);
            rs.close();

            if (tableCount < 3)
            {
                log.debug("Legacy Polopoly tables not found — skipping principalid backfill");
                return;
            }

            int updated = stmt.executeUpdate(
                "UPDATE `registeredusers` r " +
                "JOIN `component` c ON c.major = 18 " +
                "    AND c.attributeid = (SELECT id FROM `attributeid` " +
                "                         WHERE attribgroup = 'user' AND name = 'loginName' LIMIT 1) " +
                "    AND LOWER(c.value) = LOWER(r.loginname) " +
                "JOIN `externalids` e ON e.major = 18 AND e.minor = c.minor " +
                "SET r.principalid = e.externalid " +
                "WHERE r.principalid IS NULL");

            if (updated > 0)
            {
                log.info("Backfilled principalid for {} user(s) from legacy Polopoly data", updated);
            }
        }
        catch (Exception e)
        {
            log.warn("Could not backfill principalid from legacy tables: {}", e.getMessage());
        }
    }

    private boolean columnExists(Statement stmt, String table, String column) throws Exception
    {
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
        rs.next();
        boolean exists = rs.getInt(1) > 0;
        rs.close();
        return exists;
    }
}
