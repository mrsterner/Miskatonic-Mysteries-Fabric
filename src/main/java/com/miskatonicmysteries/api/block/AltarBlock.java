package com.miskatonicmysteries.api.block;

import com.miskatonicmysteries.api.interfaces.SpellCaster;
import com.miskatonicmysteries.common.feature.block.blockentity.AltarBlockEntity;
import com.miskatonicmysteries.common.handler.networking.packet.s2c.OpenSpellEditorPacket;
import com.miskatonicmysteries.common.registry.MMObjects;
import com.miskatonicmysteries.common.registry.MMParticles;
import com.miskatonicmysteries.common.util.Constants;
import com.miskatonicmysteries.common.util.InventoryUtil;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;
import static net.minecraft.state.property.Properties.WATERLOGGED;

public class AltarBlock extends HorizontalFacingBlock implements Waterloggable, BlockEntityProvider {

	public static VoxelShape SHAPE = createCuboidShape(3, 0, 3, 13, 18, 13);
	public static List<AltarBlock> ALTARS = new ArrayList<>();
	private final boolean spawnParticles;

	public AltarBlock(boolean spawnParticles, Settings settings) {
		super(settings);
		this.spawnParticles = spawnParticles;
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
		ALTARS.add(this);
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos,
												BlockPos posFrom) {
		if (state.contains(WATERLOGGED) && state.get(WATERLOGGED)) {
			world.createAndScheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		return super.getStateForNeighborUpdate(state, direction, newState, world, pos, posFrom);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof AltarBlockEntity) {
				ItemScatterer.spawn(world, pos, ((AltarBlockEntity) blockEntity).getItems());
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		ItemStack stack = player.getStackInHand(hand);
		AltarBlockEntity altar = (AltarBlockEntity) world.getBlockEntity(pos);
		if (altar != null) {
			if (!stack.isEmpty() && altar.isValid(0, stack) && altar.getStack(0).isEmpty()) {
				altar.setStack(0, stack);
				altar.markDirty();
				return ActionResult.CONSUME;
			} else if (!altar.getItems().isEmpty()) {
				if (!player.isSneaking() && !altar.getStack(0).isEmpty() && altar.getBook().equals(MMObjects.NECRONOMICON)) {
					if (!world.isClient && player instanceof SpellCaster) {
						((SpellCaster) player).syncSpellData();
						OpenSpellEditorPacket.send(player);
					}
					return ActionResult.SUCCESS;
				} else if (stack.isEmpty()) {
					InventoryUtil.giveItem(world, player, altar.removeStack(0));
					altar.markDirty();
					return ActionResult.SUCCESS;
				}
				return ActionResult.FAIL;
			}
		}
		return super.onUse(state, world, pos, player, hand, hit);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.contains(WATERLOGGED) && state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (spawnParticles && !state.get(WATERLOGGED)) {
			Direction direction = state.get(FACING);
			generateParticle(world, pos, direction, 15, 5.5, 15, 0.4F);
			generateParticle(world, pos, direction, 12, 5.5, 15, 0.4F);
			generateParticle(world, pos, direction, 13.5, 6.5, 13, 0.4F);

			generateParticle(world, pos, direction, 1.5, 7, 14, 0.6F);

			generateParticle(world, pos, direction, 1.5, 21.5, 1, 0.4F);
			generateParticle(world, pos, direction, 1, 21.5, 3.5, 0.4F);

			generateParticle(world, pos, direction, 13, 21.5, 2.5, 0.4F);

			generateParticle(world, pos, direction, 11.5, 27.5, 10.5, 0.4F);
			generateParticle(world, pos, direction, 7.5, 27.5, 11, 0.4F);
			generateParticle(world, pos, direction, 9.5, 29, 12, 0.6F);

		}
		super.randomDisplayTick(state, world, pos, random);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = this.getDefaultState().with(FACING, ctx.getPlayerFacing());
		if (state.contains(WATERLOGGED)) {
			final FluidState fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
			final boolean source = fluidState.isIn(FluidTags.WATER) && fluidState.getLevel() == 8;
			return state.with(WATERLOGGED, source);
		}
		return state;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(FACING, WATERLOGGED);
	}

	@Environment(EnvType.CLIENT)
	public void generateParticle(World world, BlockPos pos, Direction direction, double xCoord, double yCoord, double zCoord, float size) {
		//see if I can do GL rotate magic instead lol
		double x = pos.getX() + 0.5;
		double y = pos.getY();
		double z = pos.getZ() + 0.5;
		boolean reverse = direction == Direction.WEST || direction == Direction.EAST;
		int mult = direction == Direction.NORTH || direction == Direction.EAST ? -1 : 1;

		if (reverse) {
			double tempZ = zCoord;
			zCoord = xCoord;
			xCoord = tempZ;
		}
		MMParticles.spawnCandleParticle(world, x + (xCoord * Constants.BLOCK_BIT - 0.5) * (reverse ? -mult : mult),
										y + yCoord * Constants.BLOCK_BIT, z + (zCoord * Constants.BLOCK_BIT - 0.5) * mult, size, false);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new AltarBlockEntity(pos, state);
	}
}
