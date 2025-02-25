package com.artillexstudios.axinventoryrestore.commands;

import com.artillexstudios.axinventoryrestore.AxInventoryRestore;
import com.artillexstudios.axinventoryrestore.guis.MainGui;
import com.artillexstudios.axinventoryrestore.utils.MessageUtils;
import com.artillexstudios.axinventoryrestore.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

import static com.artillexstudios.axinventoryrestore.AxInventoryRestore.MESSAGES;

public class Commands implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        final Player player = (Player) sender;

        if (args.length == 1 && args[0].equals("reload")) {
            if (!PermissionUtils.hasPermission(sender, "reload")) {
                MessageUtils.sendMsgP(sender, "errors.no-permission");
                return true;
            }

            AxInventoryRestore.getAbstractConfig().reloadConfig();
            AxInventoryRestore.getAbstractMessages().reloadConfig();

            MessageUtils.sendMsgP(sender, "reloaded");
            return true;
        }

        if (args.length == 1 && args[0].equals("cleanup")) {
            if (!PermissionUtils.hasPermission(sender, "cleanup")) {
                MessageUtils.sendMsgP(sender, "errors.no-permission");
                return true;
            }

            MessageUtils.sendMsgP(sender, "cleaned-up");
            return true;
        }

        if (args.length == 1) {
            if (!PermissionUtils.hasPermission(sender, "view")) {
                MessageUtils.sendMsgP(sender, "errors.no-permission");
                return true;
            }

            final UUID uuid = AxInventoryRestore.getDB().getUUID(args[0]);
            if (uuid == null) {
                MessageUtils.sendMsgP(sender, "errors.unknown-player");
                return true;
            }

            new MainGui(uuid, player, args[0]).openMainGui();
            return true;
        }

        if (args.length == 2 && args[0].equals("save")) {
            if (!PermissionUtils.hasPermission(sender, "manualbackup")) {
                MessageUtils.sendMsgP(sender, "errors.no-permission");
                return true;
            }

            final String cause = MESSAGES.getString("manual-created-by").replace("%player%", sender.getName());

            if (args[1].equals("*")) {
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    AxInventoryRestore.getDatabaseQueue().submit(() -> {
                        AxInventoryRestore.getDB().saveInventory(pl, "MANUAL", cause);
                    });
                }

                MessageUtils.sendMsgP(sender, "manual-backup-all");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtils.sendMsgP(sender, "errors.player-offline");
                return true;
            }

            AxInventoryRestore.getDatabaseQueue().submit(() -> {
                AxInventoryRestore.getDB().saveInventory(targetPlayer, "MANUAL", cause);
            });

            MessageUtils.sendMsgP(sender, "manual-backup", Map.of("%player%", targetPlayer.getName()));
            return true;
        }

        MessageUtils.sendListMsg(sender, "help");
        return true;
    }
}
