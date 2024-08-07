/*
 * BluSunrize
 * Copyright (c) 2024
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.api.tool;

import blusunrize.immersiveengineering.api.IEApi;
import blusunrize.immersiveengineering.api.Lib;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public class MachineInterfaceHandler
{
	public static ResourceLocation BASIC_ACTIVE = new ResourceLocation(Lib.MODID, "basic/active");
	public static ResourceLocation BASIC_ITEM_IN = new ResourceLocation(Lib.MODID, "basic/item_input");
	public static ResourceLocation BASIC_ITEM_OUT = new ResourceLocation(Lib.MODID, "basic/item_output");
	public static ResourceLocation BASIC_FLUID_IN = new ResourceLocation(Lib.MODID, "basic/fluid_input");
	public static ResourceLocation BASIC_FLUID_OUT = new ResourceLocation(Lib.MODID, "basic/fluid_output");
	public static ResourceLocation BASIC_ENERGY = new ResourceLocation(Lib.MODID, "basic/energy_storage");

	private static final Map<ResourceLocation, CheckOption<?>[]> CONDITION_REGISTRY = new HashMap<>();

	public static void register(ResourceLocation key, CheckOption<?>... options)
	{
		CONDITION_REGISTRY.put(key, options);
	}

	public static void copyOptions(ResourceLocation newKey, ResourceLocation oldKey)
	{
		CONDITION_REGISTRY.put(newKey, CONDITION_REGISTRY.get(oldKey));
	}

	static
	{
		// Active, yes/no
		register(BASIC_ACTIVE,
				new CheckOption<BooleanSupplier>(new ResourceLocation(Lib.MODID, "yes"), value -> value.getAsBoolean()?15: 0),
				new CheckOption<BooleanSupplier>(new ResourceLocation(Lib.MODID, "no"), value -> value.getAsBoolean()?0: 15)
		);
		// Items
		register(BASIC_ITEM_IN, buildComparativeConditions(MachineInterfaceHandler::getInventoryFill));
		copyOptions(BASIC_ITEM_OUT, BASIC_ITEM_IN);
		// Fluids
		register(BASIC_FLUID_IN, buildComparativeConditions(MachineInterfaceHandler::getTankFill));
		copyOptions(BASIC_FLUID_OUT, BASIC_FLUID_IN);
		// Energy
		register(BASIC_ENERGY, buildComparativeConditions(MachineInterfaceHandler::getEnergyFill));
	}

	@SuppressWarnings("unchecked")
	public static <T> CheckOption<T>[] buildComparativeConditions(ToDoubleFunction<T> tdf)
	{
		return (CheckOption<T>[])new CheckOption<?>[]{
				CheckOption.doubleCondition(new ResourceLocation(Lib.MODID, "comparator"), tdf),
				CheckOption.<T>booleanCondition(new ResourceLocation(Lib.MODID, "empty"), h -> tdf.applyAsDouble(h) <= 0),
				CheckOption.<T>booleanCondition(new ResourceLocation(Lib.MODID, "quarter"), h -> tdf.applyAsDouble(h) > 0.25),
				CheckOption.<T>booleanCondition(new ResourceLocation(Lib.MODID, "half"), h -> tdf.applyAsDouble(h) > 0.5),
				CheckOption.<T>booleanCondition(new ResourceLocation(Lib.MODID, "three_quarter"), h -> tdf.applyAsDouble(h) > 0.75),
				CheckOption.<T>booleanCondition(new ResourceLocation(Lib.MODID, "full"), h -> tdf.applyAsDouble(h) >= 1)
		};
	}

	private static float getInventoryFill(IItemHandler itemHandler)
	{
		float f = 0;
		// This is a bit of a nasty workaround to deal with our own WrappingItemHandler
		// We use its isItemValid check to rule out slots not covered by the wrapping
		int validSlots = 0;
		for(int iSlot = 0; iSlot < itemHandler.getSlots(); iSlot++)
		{
			ItemStack itemstack = itemHandler.getStackInSlot(iSlot);
			if(itemHandler.isItemValid(iSlot, itemstack))
			{
				if(!itemstack.isEmpty())
					f += itemstack.getCount()/(float)Math.min(itemHandler.getSlotLimit(iSlot), itemstack.getMaxStackSize());
				validSlots++;
			}
		}
		f /= (float)validSlots;
		return f;
	}

	private static float getTankFill(IFluidHandler fluidHandler)
	{
		float f = 0;
		for(int iTank = 0; iTank < fluidHandler.getTanks(); iTank++)
		{
			FluidStack fluid = fluidHandler.getFluidInTank(iTank);
			if(!fluid.isEmpty())
				f += fluid.getAmount()/(float)fluidHandler.getTankCapacity(iTank);
		}
		f /= (float)fluidHandler.getTanks();
		return f;
	}

	private static float getEnergyFill(IEnergyStorage energyStorage)
	{
		return energyStorage.getEnergyStored()/(float)energyStorage.getMaxEnergyStored();
	}

	public record CheckOption<T>(ResourceLocation name, ToIntFunction<T> condition)
	{
		// helper method to turn boolean conditions into comparator signals
		public static <T> CheckOption<T> booleanCondition(ResourceLocation name, final Predicate<T> predicate)
		{
			return new CheckOption<>(name, value -> predicate.test(value)?15: 0);
		}

		// helper method to turn double conditions into comparator signals
		public static <T> CheckOption<T> doubleCondition(ResourceLocation name, final ToDoubleFunction<T> toDouble)
		{
			return new CheckOption<>(name, value -> Mth.ceil(Math.max(toDouble.applyAsDouble(value), 0)*15));
		}

		public Component getName()
		{
			return Component.translatable("gui."+name.getNamespace()+".config.machine_interface.option."+name.getPath());
		}

		public int getValue(T input)
		{
			return condition.applyAsInt(input);
		}
	}

	public record MachineCheckImplementation<T>(T instance, ResourceLocation key, CheckOption<T>[] options)
	{
		@SuppressWarnings("unchecked")
		public MachineCheckImplementation(T instance, ResourceLocation key)
		{
			this(instance, key, (CheckOption<T>[])CONDITION_REGISTRY.get(key));
		}

		public Component getName()
		{
			return Component.translatable("gui."+key.getNamespace()+".config.machine_interface.check."+key.getPath().replaceAll("/", "."));
		}
	}

	public interface IMachineInterfaceConnection
	{
		BlockCapability<IMachineInterfaceConnection, @Nullable Direction> CAPABILITY = BlockCapability.createSided(
				IEApi.ieLoc("machine_interface_connection"), IMachineInterfaceConnection.class
		);

		MachineCheckImplementation<?>[] getAvailableChecks();
	}
}
