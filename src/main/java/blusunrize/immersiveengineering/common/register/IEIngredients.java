/*
 * BluSunrize
 * Copyright (c) 2023
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.register;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.common.crafting.fluidaware.IngredientFluidStack;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.ForgeRegistries.Keys;
import net.neoforged.neoforge.registries.RegistryObject;

public class IEIngredients
{
	public static final DeferredRegister<IngredientType<?>> REGISTER = DeferredRegister.create(
			Keys.INGREDIENT_TYPES, ImmersiveEngineering.MODID
	);

	public static final RegistryObject<IngredientType<?>> FLUID_STACK = REGISTER.register(
			"fluid_stack",
			() -> new IngredientType<>(IngredientFluidStack.CODEC, IngredientFluidStack.CODEC)
	);
}