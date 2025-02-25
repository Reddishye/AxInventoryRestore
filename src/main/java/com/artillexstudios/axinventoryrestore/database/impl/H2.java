package com.artillexstudios.axinventoryrestore.database.impl;

import com.artillexstudios.axinventoryrestore.AxInventoryRestore;
import com.artillexstudios.axinventoryrestore.api.events.InventoryBackupEvent;
import com.artillexstudios.axinventoryrestore.database.Database;
import com.artillexstudios.axinventoryrestore.utils.BackupData;
import com.artillexstudios.axinventoryrestore.utils.LocationUtils;
import com.artillexstudios.axinventoryrestore.utils.SerializationUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.h2.jdbc.JdbcConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

public class H2 implements Database {
    private Connection conn;

    @Override
    public String getType() {
        return "H2";
    }

    @Override
    public void setup() {

        try {
            conn = new JdbcConnection("jdbc:h2:./" + AxInventoryRestore.getInstance().getDataFolder() + "/data", new Properties(), null, null, false);
            conn.setAutoCommit(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `axinventoryrestore_data` ( `player` VARCHAR(36) NOT NULL, `reason` VARCHAR(64) NOT NULL, `location` VARCHAR(256) NOT NULL, `id` INT NOT NULL, `time` BIGINT NOT NULL, `cause` VARCHAR(512), PRIMARY KEY (`id`) );";

        try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
            stmt.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }

        final String CREATE_TABLE2 = "CREATE TABLE IF NOT EXISTS `axinventoryrestore_backups` ( `id` INT NOT NULL AUTO_INCREMENT, `inventory` VARCHAR NOT NULL, PRIMARY KEY (`id`) );";

        try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE2)) {
            stmt.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void saveInventory(@NotNull Player player, @NotNull String reason, @Nullable String cause) {

        final InventoryBackupEvent inventoryBackupEvent = new InventoryBackupEvent(player, reason, cause);
        Bukkit.getPluginManager().callEvent(inventoryBackupEvent);
        if (inventoryBackupEvent.isCancelled()) return;

        boolean isEmpty = true;

        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null) continue;

            isEmpty = false;
        }

        if (isEmpty) return;

        final String ex = "INSERT INTO `axinventoryrestore_backups`(`inventory`) VALUES (?);";
        final String ex2 = "INSERT INTO `axinventoryrestore_data`(`player`, `reason`, `location`, `id`, `time`, `cause`) VALUES (?,?,?,?,?,?);";

        try (PreparedStatement stmt = conn.prepareStatement(ex, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, SerializationUtils.invToBase64(player.getInventory().getContents()));
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys(); PreparedStatement stmt2 = conn.prepareStatement(ex2)) {
                rs.next();

                stmt2.setString(1, player.getUniqueId().toString());
                stmt2.setString(2, reason);
                stmt2.setString(3, LocationUtils.serializeLocation(player.getLocation(), true));
                stmt2.setInt(4, rs.getInt(1));
                stmt2.setLong(5, System.currentTimeMillis());
                stmt2.setString(6, cause);
                stmt2.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<BackupData> getDeathsByType(@NotNull UUID uuid, @NotNull String reason) {
        final ArrayList<BackupData> backups = new ArrayList<>();

        // long time = System.currentTimeMillis();

        final String ex = "SELECT * FROM `axinventoryrestore_data` WHERE `player` = ? AND `reason` = ? ORDER BY `time` DESC;";
        final String ex2 = "SELECT `inventory` FROM `axinventoryrestore_backups` WHERE `id` = ?";

        try (PreparedStatement stmt = conn.prepareStatement(ex)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, reason);

            try (ResultSet rs = stmt.executeQuery()) {
                // System.out.println((System.currentTimeMillis() - time) + " - SELECT * FROM `axinventoryrestore_data` WHERE `player` = ? AND `reason` = ? ORDER BY `time` DESC;");

                while (rs.next()) {

                    try (PreparedStatement stmt2 = conn.prepareStatement(ex2)) {
                        stmt2.setInt(1, rs.getInt(4));

                        try (ResultSet rs2 = stmt2.executeQuery()) {
                            // System.out.println((System.currentTimeMillis() - time) + " - SELECT `inventory` FROM `axinventoryrestore_backups` WHERE `id` = ?");
                            rs2.next();
                            backups.add(new BackupData(UUID.fromString(rs.getString(1)),
                                    rs.getString(2),
                                    LocationUtils.deserializeLocation(rs.getString(3)),
                                    SerializationUtils.invFromBase64(rs2.getString(1)),
                                    rs.getLong(5),
                                    rs.getString(6)));
                        }
                    }
                }

//                // System.out.println((System.currentTimeMillis() - time) + " - SELECT * FROM `axinventoryrestore_data` WHERE `player` = ? AND `reason` = ? ORDER BY `time` DESC;");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return backups;
    }

    @Override
    public int getDeathsSizeType(@NotNull UUID uuid, @NotNull String reason) {

        // long time = System.currentTimeMillis();

        String ex = "SELECT COUNT(`id`) FROM `axinventoryrestore_data` WHERE `player` = ? AND `reason` = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(ex)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, reason);

            try (ResultSet rs = stmt.executeQuery()) {
                // System.out.println((System.currentTimeMillis() - time) + " - SELECT COUNT(`id`) FROM `axinventoryrestore_data` WHERE `player` = ? AND `reason` = ?;");
                if (rs.next())
                    return rs.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public ArrayList<String> getDeathReasons(@NotNull UUID uuid) {
        final ArrayList<String> reasons = new ArrayList<>();

        // long time = System.currentTimeMillis();

        String ex = "SELECT DISTINCT `reason` FROM `axinventoryrestore_data` WHERE `player` = ?;";
        try (PreparedStatement stmt = conn.prepareStatement(ex)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                // System.out.println((System.currentTimeMillis() - time) + " - SELECT DISTINCT `reason` FROM `axinventoryrestore_data` WHERE `player` = ?;");
                while (rs.next()) {
                    reasons.add(rs.getString("reason"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }


        return reasons;
    }

    @Override
    public void join(@NotNull Player player) {

    }

    @Nullable
    @Override
    public UUID getUUID(@NotNull String player) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player);

        if (offlinePlayer == null) return null;
        return offlinePlayer.getUniqueId();
    }

    @Override
    public void cleanup() {
        String ex = "DELETE FROM `axinventoryrestore_data` WHERE `time` < ?;";
        try (PreparedStatement stmt = conn.prepareStatement(ex)) {
            stmt.setLong(1, System.currentTimeMillis() - (86_400_000L * AxInventoryRestore.CONFIG.getLong("cleanup-after-days")));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disable() {
        try {
            conn.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
