package com.leonardobishop.quests.bukkit.tasktype.type.internal;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.tasktype.type.external.mythicmobs.Kill;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import net.Indyuce.mmocore.api.event.CustomBlockMineEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockBreak extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public BlockBreak(BukkitQuestsPlugin plugin) {
        super("blockbreak", TaskUtils.TASK_ATTRIBUTION_STRING, "Break a set amount of a block.", "blockbreakcertain");
        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useMaterialListConfigValidator(this, "block", "blocks"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "data"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "check-coreprotect"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "check-coreprotect-time"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "reverse-if-placed"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "use-similar-blocks"));

        if (Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            plugin.getServer().getPluginManager().registerEvents(new CustomBlockMineListener(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handle(event.getPlayer(), event.getBlock());
    }

    /**
     * MMOCore 提供的自定义矿物破坏事件
     * 用于自定义掉落物相关功能的触发
     */
    private final class CustomBlockMineListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCustomMine(CustomBlockMineEvent event) {
            handle(event.getPlayer(), event.getBlock());
        }
    }


    private void handle(Player player, Block block) {
        if (player.hasMetadata("NPC")) return;

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) return;

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskUtils.TaskConstraint.WORLD)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player mined block " + block.getType(), quest.getId(), task.getId(), player.getUniqueId());

            if (TaskUtils.matchBlock(this, pendingTask, block, player.getUniqueId())) {
                boolean coreProtectEnabled = (boolean) task.getConfigValue("check-coreprotect", false);
                int coreProtectTime = (int) task.getConfigValue("check-coreprotect-time", 3600);

                if (coreProtectEnabled && plugin.getCoreProtectHook() == null) {
                    super.debug("check-coreprotect is enabled, but CoreProtect is not detected on the server", quest.getId(), task.getId(), player.getUniqueId());
                }

                Runnable increment = () -> {
                    int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress);
                    super.debug("Incrementing task progress (now " + progress + ")", quest.getId(), task.getId(), player.getUniqueId());

                    int blocksNeeded = (int) task.getConfigValue("amount");

                    if (progress >= blocksNeeded) {
                        super.debug("Marking task as complete", quest.getId(), task.getId(), player.getUniqueId());
                        taskProgress.setCompleted(true);
                    }
                };

                if (coreProtectEnabled && plugin.getCoreProtectHook() != null) {
                    super.debug("Running CoreProtect lookup (may take a while)", quest.getId(), task.getId(), player.getUniqueId());
                    plugin.getCoreProtectHook().checkBlock(block, coreProtectTime).thenAccept(result -> {
                        if (result) {
                            super.debug("CoreProtect lookup indicates this is a player placed block, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                        } else {
                            super.debug("CoreProtect lookup OK", quest.getId(), task.getId(), player.getUniqueId());
                            increment.run();
                        }
                    }).exceptionally(ex -> {
                        super.debug("CoreProtect lookup failed: " + ex.getMessage(), quest.getId(), task.getId(), player.getUniqueId());
                        ex.printStackTrace();
                        return null;
                    });
                } else {
                    increment.run();
                }
            }
        }
    }


    // subtract if enabled
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasMetadata("NPC")) return;

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(event.getPlayer().getUniqueId());
        if (qPlayer == null) {
            return;
        }

        Player player = event.getPlayer();

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(event.getPlayer(), qPlayer, this, TaskUtils.TaskConstraint.WORLD)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player placed block " + event.getBlock().getType(), quest.getId(), task.getId(), event.getPlayer().getUniqueId());


            if (task.getConfigValue("reverse-if-placed") != null && ((boolean) task.getConfigValue("reverse-if-placed"))) {
                super.debug("reverse-if-placed is enabled, checking block", quest.getId(), task.getId(), event.getPlayer().getUniqueId());
                if (TaskUtils.matchBlock(this, pendingTask, event.getBlock(), player.getUniqueId())) {
                    int progress = TaskUtils.getIntegerTaskProgress(taskProgress);
                    taskProgress.setProgress(--progress);
                    super.debug("Decrementing task progress (now " + progress + ")", quest.getId(), task.getId(), player.getUniqueId());
                }
            }
        }
    }

}
