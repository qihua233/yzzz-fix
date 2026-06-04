package me.realseek.yzzzfix.mixin.ftbquests;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.net.ClaimRewardMessage;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 修复 FTB Quests 的 ClaimRewardMessage 在 Netty 网络线程上直接处理导致的
 * 竞态条件：Claim All 快速发送多个请求时，claimedRewards 的 containsKey/put
 * 不是原子操作，导致同一奖励被重复发放。
 *
 * <p>将 handle 逻辑通过 {@code ServerPlayer.getServer().execute()} 调度到主线程执行，
 * 确保奖励领取的检查与写入在同一线程中顺序完成。</p>
 */
@Mixin(value = ClaimRewardMessage.class, remap = false)
public class ClaimRewardMessageMixin {

    @Shadow
    @Final
    private long id;

    @Shadow
    @Final
    private boolean notify;

    @Overwrite(remap = false)
    public void handle(NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        player.getServer().execute(() -> {
            Reward reward = ServerQuestFile.INSTANCE.getReward(this.id);
            if (reward != null) {
                TeamData teamData = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
                if (teamData.isCompleted(reward.getQuest())) {
                    teamData.claimReward(player, reward, this.notify);
                }
            }
        });
    }
}
