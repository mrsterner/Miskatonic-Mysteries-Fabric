package com.miskatonicmysteries.common.feature.world;

import static com.miskatonicmysteries.common.util.Constants.NBT.ACTIVE;
import static com.miskatonicmysteries.common.util.Constants.NBT.IS_CORE;
import static com.miskatonicmysteries.common.util.Constants.NBT.KNOTS;
import static com.miskatonicmysteries.common.util.Constants.NBT.KNOT_POS;
import static com.miskatonicmysteries.common.util.Constants.NBT.RADIUS;
import static com.miskatonicmysteries.common.util.Constants.NBT.WARDING_MARKS;

import com.miskatonicmysteries.common.util.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

public class MMDimensionalWorldState extends PersistentState {

	private final Set<BlockPos> wardingMarks = new HashSet<>();
	private final Map<BlockPos, BiomeKnot> biomeKnots = new HashMap<>();

	public static MMDimensionalWorldState fromNbt(NbtCompound tag) {
		MMDimensionalWorldState state = new MMDimensionalWorldState();
		NbtList wardingMarksList = tag.getList(WARDING_MARKS, NbtElement.COMPOUND_TYPE);
		if (wardingMarksList != null) {
			for (NbtElement blockTag : wardingMarksList) {
				state.wardingMarks.add(NbtHelper.toBlockPos((NbtCompound) blockTag));
			}
		}
		NbtList knotList = tag.getList(KNOTS, NbtElement.COMPOUND_TYPE);
		if (knotList != null) {
			for (NbtElement knotTag : knotList) {
				BiomeKnot knot = BiomeKnot.fromNbt((NbtCompound) knotTag);
				state.biomeKnots.put(knot.pos, knot);
			}
		}
		return state;
	}

	public static MMDimensionalWorldState get(ServerWorld world) {
		return world.getPersistentStateManager()
			.getOrCreate(MMDimensionalWorldState::fromNbt, MMDimensionalWorldState::new, Constants.MOD_ID + "_dimensional");
	}

	public void addMark(BlockPos markPos) {
		wardingMarks.add(markPos);
		markDirty();
	}

	public void removeMark(BlockPos markPos) {
		wardingMarks.remove(markPos);
		markDirty();
	}

	public boolean isMarkNear(BlockPos pos, int radius) {
		for (BlockPos wardingMark : wardingMarks) {
			if (wardingMark.isWithinDistance(pos, radius)) {
				return true;
			}
		}
		return false;
	}

	public void setBiomeKnot(BlockPos pos, int radius, boolean active, boolean core) {
		if (radius <= 0) {
			biomeKnots.remove(pos);
			markDirty();
			return;
		}
		biomeKnots.compute(pos, (knotPos, knot) -> {
			if (knot == null) {
				knot = new BiomeKnot(pos, active, core, radius);
				return knot;
			}
			knot.active = active;
			knot.radius = radius;
			knot.core = core;
			return knot;
		});
		markDirty();
	}

	public List<BiomeKnot> getNearbyKnots(BlockPos pos, int ownRadius) {
		List<BiomeKnot> nearbyKnots = new ArrayList<>();
		for (BiomeKnot value : biomeKnots.values()) {
			if (value.pos.getSquaredDistance(pos) < Math.pow(value.radius + ownRadius, 2)) {
				nearbyKnots.add(value);
			}
		}
		return nearbyKnots;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound tag) {
		NbtList wardingMarksList = new NbtList();
		for (BlockPos wardingMark : wardingMarks) {
			wardingMarksList.add(NbtHelper.fromBlockPos(wardingMark));
		}
		tag.put(WARDING_MARKS, wardingMarksList);

		NbtList knotList = new NbtList();
		for (BiomeKnot knot : biomeKnots.values()) {
			knotList.add(knot.writeNbt());
		}
		tag.put(KNOTS, knotList);
		return tag;
	}

	public static class BiomeKnot {
		private final BlockPos pos;
		private boolean active, core;
		private int radius;

		private BiomeKnot(BlockPos pos, boolean active, boolean core, int radius) {
			this.pos = pos;
			this.radius = radius;
			this.active = active;
			this.core = core;
		}

		public static BiomeKnot fromNbt(NbtCompound knotTag) {
			BlockPos pos = NbtHelper.toBlockPos(knotTag.getCompound(KNOT_POS));
			return new BiomeKnot(pos, knotTag.getBoolean(ACTIVE), knotTag.getBoolean(IS_CORE), knotTag.getInt(RADIUS));
		}

		public NbtCompound writeNbt() {
			NbtCompound compound = new NbtCompound();
			compound.put(KNOT_POS, NbtHelper.fromBlockPos(pos));
			compound.putBoolean(ACTIVE, active);
			compound.putBoolean(IS_CORE, core);
			compound.putInt(RADIUS, radius);
			return compound;
		}

		public BlockPos getPos() {
			return pos;
		}

		public int getRadius() {
			return radius;
		}

		public boolean isActive() {
			return active;
		}

		public boolean isCore() {
			return core;
		}
	}
}
