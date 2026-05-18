package me.realseek.yzzzfix.mixin.moonstone;

import com.moonstone.moonstonemod.Handler;
import com.moonstone.moonstonemod.init.Items;
import com.moonstone.moonstonemod.item.blood.magic.blood_magic_box;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 修复血之魔盒在生物死亡时生成追踪实体的逻辑。
 * <p>改为直接在玩家脚下生成血球物品实体，解决背包满时吞物品及性能问题。
 * 同时适配车万女仆等仆从击杀，通过追溯主人正确发放物品。</p>
 */
@Mixin(value = blood_magic_box.class, remap = false)
public abstract class BloodMagicBoxMixin {
    @Inject(method = "Did", at = @At("HEAD"), cancellable = true, remap = false)
    private static void yzzzfix$spawnItemAtPlayer(LivingDeathEvent event, CallbackInfo ci) {

        if (event.getEntity().level().isClientSide()) return;

        Entity source = event.getSource().getEntity();
        if (source == null) return;

        Player player = yzzzfix$findPlayer(source);
        if (player == null || !Handler.hascurio(player, Items.blood_magic_box.get())) return;

        ci.cancel();

        ItemStack blood = new ItemStack(Items.blood.get());
        ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), blood);
        itemEntity.setPickUpDelay(0);
        player.level().addFreshEntity(itemEntity);
    }

    @Unique
    private static Player yzzzfix$findPlayer(Entity source) {
        if (source instanceof Player player) return player;

        if (source instanceof TamableAnimal tamable && tamable.getOwner() instanceof Player player) {
            return player;
        }

        if (source instanceof OwnableEntity ownable) {
            UUID ownerUuid = ownable.getOwnerUUID();
            if (ownerUuid != null) {
                return source.level().getPlayerByUUID(ownerUuid);
            }
        }

        return null;
    }
}