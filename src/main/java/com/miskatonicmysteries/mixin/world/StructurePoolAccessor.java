package com.miskatonicmysteries.mixin.world;

import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;

import java.util.List;

import com.mojang.datafixers.util.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructurePool.class)
public interface StructurePoolAccessor {

	@Accessor(value = "elements")
	List<StructurePoolElement> getElements();

	@Accessor(value = "elementCounts")
	List<Pair<StructurePoolElement, Integer>> getElementCounts();
}
