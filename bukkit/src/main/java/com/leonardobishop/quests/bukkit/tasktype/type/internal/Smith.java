package com.leonardobishop.quests.bukkit.tasktype.type.internal;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.item.QuestItem;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;

public final class Smith extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;
    private final Table<String, String, QuestItem> fixedQuestItemCache = HashBasedTable.create();

    public Smith(BukkitQuestsPlugin plugin) {
        super("smithing", TaskUtils.TASK_ATTRIBUTION_STRING, "Smith a specific item.");
        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "item"));
        super.addConfigValidator(TaskUtils.useItemStackConfigValidator(this, "item"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "data"));
    }

    @Override
    public void onLoad() {
        fixedQuestItemCache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithItem(SmithItemEvent event) {
        //noinspection DuplicatedCode
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR
                || event.getAction() == InventoryAction.NOTHING
                || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD && event.getClick() == ClickType.NUMBER_KEY && !plugin.getVersionSpecificHandler().isHotbarMoveAndReaddSupported() // https://discord.com/channels/211910297810632704/510553623022010371/1011035743331819550
                || event.getAction() == InventoryAction.DROP_ONE_SLOT && event.getClick() == ClickType.DROP && (event.getCursor() != null && event.getCursor().getType() != Material.AIR) // https://github.com/LMBishop/Quests/issues/430
                || event.getAction() == InventoryAction.DROP_ALL_SLOT && event.getClick() == ClickType.CONTROL_DROP && (event.getCursor() != null && event.getCursor().getType() != Material.AIR) // https://github.com/LMBishop/Quests/issues/430
                || event.getAction() == InventoryAction.UNKNOWN && event.getClick() == ClickType.UNKNOWN // for better ViaVersion support
                || !(event.getWhoClicked() instanceof Player player)
                || plugin.getVersionSpecificHandler().isOffHandSwap(event.getClick()) && !plugin.getVersionSpecificHandler().isOffHandEmpty(player)) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        ItemStack item = event.getCurrentItem();

        int eventAmount = item.getAmount();
        if (event.isShiftClick() && event.getClick() != ClickType.CONTROL_DROP) { // https://github.com/LMBishop/Quests/issues/317
            // https://cdn.discordapp.com/attachments/510553623022010371/1011483223446007849/unknown.png
            eventAmount *= Math.min(event.getInventory().getInputEquipment().getAmount(), event.getInventory().getInputMineral().getAmount());
            eventAmount = Math.min(eventAmount, plugin.getVersionSpecificHandler().getAvailableSpace(player, item));
            if (eventAmount == 0) {
                return;
            }
        }

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskUtils.TaskConstraint.WORLD)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            QuestItem qi;
            if ((qi = fixedQuestItemCache.get(quest.getId(), task.getId())) == null) {
                QuestItem fetchedItem = TaskUtils.getConfigQuestItem(task, "item", "data");
                fixedQuestItemCache.put(quest.getId(), task.getId(), fetchedItem);
                qi = fetchedItem;
            }

            super.debug("Player smithed " + eventAmount +  " of " + item.getType(), quest.getId(), task.getId(), player.getUniqueId());

            if (!qi.compareItemStack(item)) {
                super.debug("Item does not match, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            int amount = (int) task.getConfigValue("amount");

            int progress = TaskUtils.getIntegerTaskProgress(taskProgress);
            taskProgress.setProgress(progress + eventAmount);
            super.debug("Updating task progress (now " + (progress + eventAmount) + ")", quest.getId(), task.getId(), player.getUniqueId());

            if ((int) taskProgress.getProgress() >= amount) {
                super.debug("Marking task as complete", quest.getId(), task.getId(), player.getUniqueId());
                taskProgress.setProgress(amount);
                taskProgress.setCompleted(true);
            }
        }
    }
}
