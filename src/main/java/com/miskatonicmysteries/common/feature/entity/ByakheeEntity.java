package com.miskatonicmysteries.common.feature.entity;

import com.miskatonicmysteries.api.interfaces.Affiliated;
import com.miskatonicmysteries.api.registry.Affiliation;
import com.miskatonicmysteries.common.feature.entity.util.InputAware;
import com.miskatonicmysteries.common.registry.MMAffiliations;
import com.miskatonicmysteries.common.registry.MMSounds;
import com.miskatonicmysteries.common.util.Constants;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

public class ByakheeEntity extends TameableEntity implements Saddleable, InputAware, Affiliated {

	private static final TrackedData<Boolean> GLIDING = DataTracker.registerData(ByakheeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> SADDLED = DataTracker.registerData(ByakheeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> DECORATED = DataTracker.registerData(ByakheeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public final SimpleInventory items = new SimpleInventory(2);
	public int flapTicks = 0;
	public int ticksOffGround = 0;
	public int headShakeTicks;

	public ByakheeEntity(EntityType<? extends TameableEntity> entityType, World world) {
		super(entityType, world);
		this.stepHeight = 1;
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(2, new BondWithPlayerGoal());
		this.goalSelector.add(3, new WanderAroundGoal(this, 0.7D));
		this.goalSelector.add(4, new MeleeAttackGoal(this, 1.0D, true));
		this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
		this.goalSelector.add(6, new LookAroundGoal(this));
		this.targetSelector.add(1, (new RevengeGoal(this)).setGroupRevenge());
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return super.createNavigation(world);
	}

	@Override
	public int getMaxHeadRotation() {
		return 10;
	}

	@Override
	public boolean canBeControlledByRider() {
		return getPrimaryPassenger() instanceof LivingEntity && isOwner((LivingEntity) getPrimaryPassenger());
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean bl = target.damage(DamageSource.mob(this), (float) ((int) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)));
		if (bl) {
			this.applyDamageEffects(this, target);
		}
		return bl;
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(GLIDING, false);
		this.dataTracker.startTracking(SADDLED, false);
		this.dataTracker.startTracking(DECORATED, false);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound tag) {
		super.writeCustomDataToNbt(tag);
		tag.putBoolean(Constants.NBT.GLIDING, isGliding());
		if (!this.items.getStack(0).isEmpty()) {
			tag.put("SaddleItem", this.items.getStack(0).writeNbt(new NbtCompound()));
		}
		if (!this.items.getStack(1).isEmpty()) {
			tag.put("DecoItem", this.items.getStack(1).writeNbt(new NbtCompound()));
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound tag) {
		super.readCustomDataFromNbt(tag);
		setGliding(tag.getBoolean(Constants.NBT.GLIDING));
		if (tag.contains("SaddleItem", 10)) {
			ItemStack itemStack = ItemStack.fromNbt(tag.getCompound("SaddleItem"));
			if (itemStack.getItem() == Items.SADDLE) {
				this.items.setStack(0, itemStack);
			}
		}
		if (tag.contains("DecoItem", 10)) {
			ItemStack itemStack = ItemStack.fromNbt(tag.getCompound("DecoItem"));
			if (itemStack.getItem() == Items.YELLOW_CARPET) {
				this.items.setStack(1, itemStack);
			}
		}
		updateSaddle();
	}

	public void updateSaddle() {
		if (!world.isClient) {
			dataTracker.set(SADDLED, !items.getStack(0).isEmpty());
			dataTracker.set(DECORATED, !items.getStack(1).isEmpty());
		}
	}

	public boolean isGliding() {
		return dataTracker.get(GLIDING);
	}

	public void setGliding(boolean gliding) {
		dataTracker.set(GLIDING, gliding);
	}

	public boolean bondWithPlayer(PlayerEntity player) {
		this.setOwnerUuid(player.getUuid());
		this.setTamed(true);
		if (player instanceof ServerPlayerEntity) {
			Criteria.TAME_ANIMAL.trigger((ServerPlayerEntity) player, this);
		}
		this.world.sendEntityStatus(this, (byte) 7);
		return true;
	}

	@Override
	public boolean canBeSaddled() {
		return isTamed();
	}

	@Override
	public void saddle(@Nullable SoundCategory sound) {
		this.items.setStack(0, new ItemStack(Items.SADDLE));
		if (sound != null) {
			this.world.playSoundFromEntity(null, this, MMSounds.ENTITY_BYAKHEE_SADDLE, sound, 0.5F, 1.0F);
		}
		updateSaddle();
	}

	@Override
	public boolean isSaddled() {
		return dataTracker.get(SADDLED);
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		if (headShakeTicks > 0) {
			headShakeTicks--;
		}
		if (!isOnGround()) {
			if (!world.isClient && ++ticksOffGround > 10) {
				setGliding(true);
			}
		} else {
			ticksOffGround = 0;
			setGliding(false);
		}
		if (isLogicalSideForUpdatingMovement() && flapTicks > 0) {
			if (flapTicks > 10) {
				addVelocity(0, 0.2F, 0);
			}
			flapTicks--;
		}
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack itemStack = player.getStackInHand(hand);
		if (this.hasPassengers()) {
			return super.interactMob(player, hand);
		}

		if (!itemStack.isEmpty()) {
			if (!isTamed()) {
				shakeHead();
				return ActionResult.success(world.isClient);
			}
			if (!isDecorated() && itemStack.getItem() == Items.YELLOW_CARPET) {
				itemStack.decrement(1);
				this.items.setStack(1, new ItemStack(Items.YELLOW_CARPET));
				this.world.playSoundFromEntity(null, this, SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.NEUTRAL, 0.5F, 1.0F);
				updateSaddle();
				return ActionResult.success(this.world.isClient);
			}
			ActionResult actionResult = itemStack.useOnEntity(player, this, hand);
			if (actionResult.isAccepted()) {
				return actionResult;
			}
		}
		this.putPlayerOnBack(player);
		return ActionResult.success(this.world.isClient);
	}

	private void shakeHead() {
		if (headShakeTicks <= 0) {
			this.headShakeTicks = 20;
		}
		this.world.sendEntityStatus(this, (byte) 6);
	}

	public boolean isDecorated() {
		return dataTracker.get(DECORATED);
	}

	protected void putPlayerOnBack(PlayerEntity player) {
		if (!this.world.isClient) {
			player.setYaw(getYaw());
			player.setPitch(getPitch());
			player.startRiding(this);
		}
	}

	@Override
	public void updatePassengerPosition(Entity passenger) {
		PositionUpdater updater = Entity::setPosition;
		float x = MathHelper.sin(this.bodyYaw * 0.017453292F);
		float z = MathHelper.cos(this.bodyYaw * 0.017453292F);
		updater.accept(passenger, this.getX() + x * 0.5, this.getY() + this.getMountedHeightOffset() + passenger.getHeightOffset(),
					   this.getZ() - z * 0.5F);
	}

	@Override
	public double getMountedHeightOffset() {
		return getDimensions(getPose()).height * 0.9F;
	}

	@Nullable
	@Override
	public Entity getPrimaryPassenger() {
		return this.getPassengerList().isEmpty() ? null : this.getPassengerList().get(0);
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {

	}

	@Override
	protected void dropInventory() {
		super.dropInventory();
		if (this.items != null) {
			for (int i = 0; i < this.items.size(); ++i) {
				ItemStack itemStack = this.items.getStack(i);
				if (!itemStack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemStack)) {
					this.dropStack(itemStack);
				}
			}

		}
	}

	@Override
	public void travel(Vec3d movementInput) {
		if (this.isAlive()) {
			if (this.hasPassengers() && canBeControlledByRider() && isSaddled()) {
				LivingEntity livingEntity = (LivingEntity) this.getPrimaryPassenger();
				this.setYaw(livingEntity.getYaw());
				this.prevYaw = this.getYaw();
				this.setPitch(livingEntity.getPitch() * 0.5F);
				this.setRotation(this.getYaw(), this.getPitch());
				this.bodyYaw = this.getYaw();
				this.headYaw = this.bodyYaw;
				float f = livingEntity.sidewaysSpeed * 0.5F;
				float g = livingEntity.forwardSpeed;
				if (g <= 0.0F) {
					g *= 0.25F;
				}
				if (this.onGround) {
					Vec3d vec3d = this.getVelocity();
					this.setVelocity(vec3d.x, vec3d.y, vec3d.z);
					this.velocityDirty = true;
					if (g > 0.0F) {
						float i = MathHelper.sin(this.getYaw() * 0.017453292F);
						float j = MathHelper.cos(this.getYaw() * 0.017453292F);
						this.setVelocity(this.getVelocity().add(-0.01F * i, 0.0D, 0.01F * j));
					}
				}

				this.airStrafingSpeed = this.getMovementSpeed() / 2F;
				if (this.isLogicalSideForUpdatingMovement()) {
					this.setMovementSpeed((float) this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
					super.travel(new Vec3d(f, movementInput.y, g));
					if (isGliding()) {
						this.fallDistance = 0;
						if (getVelocity().y < -0.4F) {
							setVelocity(getVelocity().x, -0.4F, getVelocity().z);
						}
					}
				} else if (livingEntity instanceof PlayerEntity) {
					this.setVelocity(Vec3d.ZERO);
				}

				this.updateLimbs(this, false);
			} else {
				this.airStrafingSpeed = 0.02F;
				super.travel(movementInput);
				if (isGliding()) {
					this.fallDistance = 0;
					if (getVelocity().y < -0.4F) {
						setVelocity(getVelocity().x, -0.4F, getVelocity().z);
					}
				}
			}
		}
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null; //unbreedable
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void handleInput(boolean jumping) {
		if (jumping && flapTicks <= 0 && canBeControlledByRider() && isSaddled()) {
			flapTicks = 20;
		}
	}

	@Override
	public Affiliation getAffiliation(boolean apparent) {
		return MMAffiliations.HASTUR;
	}

	@Override
	public boolean isSupernatural() {
		return true;
	}

	class BondWithPlayerGoal extends Goal {

		private double targetX;
		private double targetY;
		private double targetZ;

		public BondWithPlayerGoal() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		public boolean canStart() {
			if (!isTamed() && hasPassengers()) {
				Vec3d vec3d = NoPenaltyTargeting.find(ByakheeEntity.this, 5, 4);
				if (vec3d == null) {
					return false;
				} else {
					this.targetX = vec3d.x;
					this.targetY = vec3d.y;
					this.targetZ = vec3d.z;
					return true;
				}
			} else {
				return false;
			}
		}

		public boolean shouldContinue() {
			return !isTamed() && !getNavigation().isIdle() && hasPassengers();
		}

		public void start() {
			getNavigation().startMovingTo(this.targetX, this.targetY, this.targetZ, 0.6F);
		}

		public void tick() {
			if (!isTamed() && getRandom().nextInt(50) == 0) {
				Entity entity = getPassengerList().get(0);
				if (entity == null) {
					return;
				}

				if (entity instanceof PlayerEntity) {
					if (getRandom().nextInt(5) == 0) {
						bondWithPlayer((PlayerEntity) entity);
						return;
					} else if (getRandom().nextInt(5) == 0) {
						setAttacking((PlayerEntity) entity);
					}
				}
				removeAllPassengers();
				shakeHead();
				world.sendEntityStatus(ByakheeEntity.this, (byte) 6);
			}

		}
	}
}
