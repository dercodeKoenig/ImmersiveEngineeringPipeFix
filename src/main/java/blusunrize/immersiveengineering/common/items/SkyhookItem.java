/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.items;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.Lib.DamageTypes;
import blusunrize.immersiveengineering.api.tool.IElectricEquipment;
import blusunrize.immersiveengineering.api.wires.Connection;
import blusunrize.immersiveengineering.api.wires.utils.WireUtils;
import blusunrize.immersiveengineering.common.entities.SkyhookUserData;
import blusunrize.immersiveengineering.common.entities.SkyhookUserData.SkyhookStatus;
import blusunrize.immersiveengineering.common.entities.SkylineHookEntity;
import blusunrize.immersiveengineering.common.gui.IESlot;
import blusunrize.immersiveengineering.common.register.IEDataAttachments;
import blusunrize.immersiveengineering.common.register.IEItems;
import blusunrize.immersiveengineering.common.util.IEDamageSources.ElectricDamageSource;
import blusunrize.immersiveengineering.common.util.IELogger;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.SkylineHelper;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@EventBusSubscriber(modid = Lib.MODID)
public class SkyhookItem extends UpgradeableToolItem implements IElectricEquipment
{
	public SkyhookItem()
	{
		super(new Properties().stacksTo(1), "SKYHOOK");
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> list, TooltipFlag flag)
	{
		list.add(Component.translatable(Lib.DESC_FLAVOUR+"skyhook").withStyle(ChatFormatting.GRAY));
		if(shouldLimitSpeed(stack))
			list.add(Component.translatable(Lib.DESC_FLAVOUR+"skyhook.speedLimit").withStyle(ChatFormatting.GRAY));
		else
			list.add(Component.translatable(Lib.DESC_FLAVOUR+"skyhook.noLimit").withStyle(ChatFormatting.GRAY));
	}

	private static final String LIMIT_SPEED = "limitSpeed";

	public static boolean shouldLimitSpeed(ItemStack stack)
	{
		return ItemNBTHelper.getBoolean(stack, LIMIT_SPEED);
	}

	public static void setLimitSpeed(ItemStack stack, boolean doLimit)
	{
		ItemNBTHelper.putBoolean(stack, LIMIT_SPEED, doLimit);
	}

	public static boolean toggleSpeedLimit(ItemStack stack)
	{
		CompoundTag nbt = stack.getOrCreateTag();
		boolean wasActive = nbt.getBoolean(LIMIT_SPEED);
		nbt.putBoolean(LIMIT_SPEED, !wasActive);
		return !wasActive;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity ent, int slot, boolean inHand)
	{
		super.inventoryTick(stack, world, ent, slot, inHand);
	}

	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack)
	{
		Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
		if(slot==EquipmentSlot.MAINHAND)
		{
			builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
					BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 5, AttributeModifier.Operation.ADDITION
			));
			builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
					BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.4, AttributeModifier.Operation.ADDITION
			));
		}
		return builder.build();
	}

	@SubscribeEvent
	public static void criticalHit(CriticalHitEvent ev)
	{
		ItemStack heldItem = ev.getEntity().getMainHandItem();
		if(heldItem.is(IEItems.Misc.SKYHOOK.asItem())&&getUpgradesStatic(heldItem).getBoolean("maceAttack")&&ev.isVanillaCritical())
		{
			// This is a similar formula to the mace inflicts in 1.21, but we can't do a flat damage bonus,
			// so we approximate with a multiplier
			float fallDistance = ev.getEntity().fallDistance;
			if(fallDistance < 1.5)
				return;
			float damageBonus;
			if(fallDistance <= 3)
				damageBonus = 0.66f*fallDistance; // 66% / 4 damage for the first 3 blocks
			else if(fallDistance <= 8)
				damageBonus = 2f+0.33f*(fallDistance-3); // 33% / 2 damage for the next 5 blocks
			else
				damageBonus = 3.65f+0.165f*(fallDistance-8); // 16.5% / 1 damage for the rest of the way
			ev.setDamageModifier(ev.getDamageModifier()+damageBonus);
			// also reset fall damage on a successful attack
			ev.getEntity().fallDistance = 0;
		}
	}

	@Nonnull
	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, @Nonnull InteractionHand hand)
	{

		ItemStack stack = player.getItemInHand(hand);
		if(player.getCooldowns().isOnCooldown(this))
			return new InteractionResultHolder<>(InteractionResult.PASS, stack);
		if(player.isShiftKeyDown())
		{
			boolean limitSpeed = toggleSpeedLimit(stack);
			if(limitSpeed)
				player.displayClientMessage(Component.translatable("chat.immersiveengineering.info.skyhookLimited"), true);
			else
				player.displayClientMessage(Component.translatable("chat.immersiveengineering.info.skyhookUnlimited"), true);
		}
		else
		{
			SkyhookUserData data = player.getData(IEDataAttachments.SKYHOOK_USER.get());
			if(data.hook!=null&&!world.isClientSide)
			{
				data.dismount();
				IELogger.logger.info("Player left voluntarily");
			}
			else
			{
				data.startHolding();
				player.startUsingItem(hand);
			}
		}
		return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity player, ItemStack stack, int count)
	{
		super.onUseTick(level, player, stack, count);
		SkyhookUserData data = player.getData(IEDataAttachments.SKYHOOK_USER.get());
		if(data.getStatus()!=SkyhookStatus.HOLDING_CONNECTING)
			return;
		Connection con = WireUtils.getConnectionMovedThrough(level, player);
		if(con!=null)
			SkylineHelper.spawnHook(player, con, player.getUsedItemHand(), shouldLimitSpeed(stack), getSlopeModifier(stack));
	}

	@Override
	public void releaseUsing(ItemStack stack, Level worldIn, LivingEntity player, int timeLeft)
	{
		super.releaseUsing(stack, worldIn, player, timeLeft);
		if(!worldIn.isClientSide)
			player.getData(IEDataAttachments.SKYHOOK_USER.get()).release();
	}

	public float getSlopeModifier(ItemStack stack)
	{
		CompoundTag upgrades = this.getUpgrades(stack);
		if(upgrades.contains("slopeModifier"))
			return Math.max(upgrades.getFloat("slopeModifier"), 0.5f);
		return 1;
	}

	@Override
	public void onStrike(ItemStack equipped, EquipmentSlot eqSlot, LivingEntity owner, Map<String, Object> cache, @Nullable DamageSource dmg, ElectricSource desc)
	{
		if(dmg instanceof ElectricDamageSource eds&&dmg.is(DamageTypes.WIRE_SHOCK)&&this.getUpgrades(equipped).getBoolean("insulated")
				&&(owner.getVehicle() instanceof SkylineHookEntity||owner.isUsingItem())) // either on a wire or trying to attach
		{
			eds.dmg = 0;
		}
	}

	@Override
	public int getUseDuration(ItemStack stack)
	{
		return 72000;
	}

	@Override
	public boolean canModify(ItemStack stack)
	{
		return true;
	}

	@Override
	public Slot[] getWorkbenchSlots(AbstractContainerMenu container, ItemStack stack, Level level, Supplier<Player> getPlayer, IItemHandler toolInventory)
	{
		return new Slot[]{
				new IESlot.Upgrades(container, toolInventory, 0, 102, 42, "SKYHOOK", stack, true, level, getPlayer),
				new IESlot.Upgrades(container, toolInventory, 1, 102, 22, "SKYHOOK", stack, true, level, getPlayer),
		};
	}

	@Override
	public int getSlotCount()
	{
		return 2;
	}
}