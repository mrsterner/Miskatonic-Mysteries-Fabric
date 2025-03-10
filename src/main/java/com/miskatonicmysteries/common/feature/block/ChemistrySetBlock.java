package com.miskatonicmysteries.common.feature.block;

import com.miskatonicmysteries.common.feature.block.blockentity.ChemistrySetBlockEntity;
import com.miskatonicmysteries.common.util.InventoryUtil;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
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

import java.util.Random;

import static net.minecraft.state.property.Properties.LIT;
import static net.minecraft.state.property.Properties.WATERLOGGED;
import org.jetbrains.annotations.Nullable;

public class ChemistrySetBlock extends HorizontalFacingBlock implements BlockEntityProvider, Waterloggable {

	public static final VoxelShape SHAPE_S_N = createCuboidShape(0, 0, 2, 16, 14, 14);
	public static final VoxelShape SHAPE_W_E = createCuboidShape(2, 0, 0, 14, 14, 16);

	public ChemistrySetBlock() {
		super(Settings.of(Material.METAL).nonOpaque().requiresTool().strength(1F, 4F)
				  .allowsSpawning((state, world, pos, type) -> false).solidBlock((state, world, pos) -> false)
				  .suffocates((state, world, pos) -> false)
				  .blockVision((state, world, pos) -> false)
				  .luminance((state -> state.get(LIT) ? 10 : 0)));
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LIT, false).with(WATERLOGGED, false));
	}

	@Override
	public boolean isTranslucent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		super.randomDisplayTick(state, world, pos, random);
		if (state.get(LIT)) {
			world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5F + random.nextGaussian() / 3F,
							  pos.getY() + 0.5F + random.nextGaussian() / 4F, pos.getZ() + 0.5F + random.nextGaussian() / 3F, 0, 0, 0);
		} else if (world.getBlockEntity(pos) instanceof ChemistrySetBlockEntity) {
			ChemistrySetBlockEntity cheemset = (ChemistrySetBlockEntity) world.getBlockEntity(pos);
			if (cheemset.containsPotentialItems()) {
				cheemset.getPotentialItems().forEach(p -> {
					if (!p.isEmpty() && random.nextBoolean()) {
						world.addParticle(ParticleTypes.ENTITY_EFFECT, pos.getX() + 0.5F + random.nextGaussian() / 4F,
										  pos.getY() + 0.5F + random.nextGaussian() / 3F, pos.getZ() + 0.5F + random.nextGaussian() / 4F,
										  cheemset.smokeColor[0] / 255F, cheemset.smokeColor[1] / 255F, cheemset.smokeColor[2] / 255F);
					}
				});
			}
		}
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
		builder.add(LIT, FACING, WATERLOGGED);
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
			if (blockEntity instanceof ChemistrySetBlockEntity) {
				ItemScatterer.spawn(world, pos, ((ChemistrySetBlockEntity) blockEntity).getItems());
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		ItemStack stack = player.getStackInHand(hand);
		ChemistrySetBlockEntity blockEntity = (ChemistrySetBlockEntity) world.getBlockEntity(pos);
		if (stack.getItem() instanceof FlintAndSteelItem && !state.get(LIT) && !state.get(WATERLOGGED)) {
			stack.damage(1, player, (p) -> p.sendToolBreakStatus(hand));
			world.playSound(player, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F,
							world.random.nextFloat() * 0.4F + 0.8F);
			world.setBlockState(pos, state.with(LIT, blockEntity.canBeLit(player)));
			return ActionResult.SUCCESS;
		} else if (!stack.isEmpty()) {
			if (blockEntity.containsPotentialItems()) {
				blockEntity.markDirty();
				if (!world.isClient) {
					blockEntity.sync(world, pos);
				}
				if (blockEntity.convertPotentialItem(player, hand)) {
					return ActionResult.SUCCESS;
				}
			}
			for (int i = 0; i < blockEntity.size(); i++) {
				if (blockEntity.getStack(i).isEmpty() && blockEntity.isValid(i, stack) && blockEntity.canPlayerUse(player)) {
					blockEntity.setStack(i, stack.split(1));
					blockEntity.markDirty();
					if (!world.isClient) {
						blockEntity.sync(world, pos);
					}
					return ActionResult.CONSUME;
				}
			}
		} else {
			for (int i = 5; i >= 0; i--) {
				if (!blockEntity.getStack(i).isEmpty() && blockEntity.canPlayerUse(player)) {
					InventoryUtil.giveItem(world, player, blockEntity.removeStack(i));
					blockEntity.markDirty();
					if (!world.isClient) {
						blockEntity.sync(world, pos);
					}
					return ActionResult.SUCCESS;
				}
			}
		}
		return super.onUse(state, world, pos, player, hand, hit);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.contains(WATERLOGGED) && state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Environment(EnvType.CLIENT)
	public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
		return 1.0F;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return (state.get(FACING) == Direction.NORTH || state.get(FACING) == Direction.SOUTH) ? SHAPE_S_N : SHAPE_W_E;
	}

	@Override
	public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState) {
		if (!state.get(Properties.WATERLOGGED) && fluidState.getFluid() == Fluids.WATER) {
			if (!world.isClient()) {
				world.setBlockState(pos, state.with(Properties.WATERLOGGED, true).with(LIT, false), 3);
				world.createAndScheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
			}
			return true;
		}
		return false;
	}


	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ChemistrySetBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return (world1, pos, state1, blockEntity) -> ChemistrySetBlockEntity.tick((ChemistrySetBlockEntity) blockEntity);
	}
}
