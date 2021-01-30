package com.miskatonicmysteries.mixin;

import com.miskatonicmysteries.common.MiskatonicMysteries;
import com.miskatonicmysteries.common.entity.ProtagonistEntity;
import com.miskatonicmysteries.common.feature.Affiliation;
import com.miskatonicmysteries.common.feature.blessing.Blessing;
import com.miskatonicmysteries.common.feature.effect.LazarusStatusEffect;
import com.miskatonicmysteries.common.feature.interfaces.Ascendant;
import com.miskatonicmysteries.common.feature.interfaces.MalleableAffiliated;
import com.miskatonicmysteries.common.feature.interfaces.Sanity;
import com.miskatonicmysteries.common.feature.interfaces.SpellCaster;
import com.miskatonicmysteries.common.feature.spell.Spell;
import com.miskatonicmysteries.common.feature.spell.SpellEffect;
import com.miskatonicmysteries.common.feature.spell.SpellMedium;
import com.miskatonicmysteries.common.handler.InsanityHandler;
import com.miskatonicmysteries.common.handler.networking.packet.SyncSpellCasterDataPacket;
import com.miskatonicmysteries.common.handler.networking.packet.s2c.ExpandSanityPacket;
import com.miskatonicmysteries.common.handler.networking.packet.s2c.RemoveExpansionPacket;
import com.miskatonicmysteries.common.handler.networking.packet.s2c.SyncBlessingsPacket;
import com.miskatonicmysteries.common.lib.Constants;
import com.miskatonicmysteries.common.lib.MMMiscRegistries;
import com.miskatonicmysteries.common.lib.MMObjects;
import com.miskatonicmysteries.common.lib.util.CapabilityUtil;
import com.miskatonicmysteries.common.lib.util.InventoryUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.miskatonicmysteries.common.lib.Constants.DataTrackers.*;

@Mixin(PlayerEntity.class)
public abstract class PlayerMixin extends LivingEntity implements Sanity, MalleableAffiliated, SpellCaster, Ascendant {
    public final Map<String, Integer> sanityCapOverrides = new ConcurrentHashMap<>();

    private final List<Spell> spells = new ArrayList<>();
    private final Set<SpellEffect> learnedEffects = new HashSet<>();
    private final Set<SpellMedium> learnedMediums = new HashSet<>();

    private final List<Blessing> blessings = new ArrayList<>();
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void handleMiskStats(CallbackInfo info) {
        for (Blessing blessing : blessings) {
            blessing.tick(this);
        }
        if (age % MiskatonicMysteries.config.modUpdateInterval == 0) {
            if (isShocked() && random.nextFloat() < MiskatonicMysteries.config.sanity.shockRemoveChance) {
                setShocked(false);
            }
        }
        if (!world.isClient && age > 100 && age % MiskatonicMysteries.config.sanity.insanityInterval == 0) {
            InsanityHandler.handleInsanityEvents((PlayerEntity) (Object) this);
        }
    }

    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"))
    private void manipulateProtagonistDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> infoReturnable) {
        if (source.getAttacker() instanceof ProtagonistEntity && !(source instanceof Constants.DamageSources.ProtagonistDamageSource))
            ((PlayerEntity) (Object) this).damage(new Constants.DamageSources.ProtagonistDamageSource(source.getAttacker()), amount);
    }

    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("RETURN"), cancellable = true)
    private void manipulateDeath(DamageSource source, float amount, CallbackInfoReturnable<Boolean> infoReturnable) {
        if (amount >= getHealth() && !source.isOutOfWorld()) {
            PlayerEntity entity = (PlayerEntity) (Object) this;
            if (InventoryUtil.getSlotForItemInHotbar(entity, MMObjects.RE_AGENT_SYRINGE) >= 0) {
                entity.inventory.removeStack(InventoryUtil.getSlotForItemInHotbar(entity, MMObjects.RE_AGENT_SYRINGE), 1);
                if (LazarusStatusEffect.revive(entity)) {
                    dead = false;
                    removed = false;
                    infoReturnable.setReturnValue(false);
                    infoReturnable.cancel();
                }
            } else if (isDead() && source instanceof Constants.DamageSources.ProtagonistDamageSource) {
                CapabilityUtil.resetProgress((PlayerEntity) (Object) this);
                if (source.getSource() instanceof ProtagonistEntity) {
                    ((ProtagonistEntity) source.getAttacker()).removeAfterTargetKill();
                }
            }
        }
    }

    @Inject(method = "initDataTracker()V", at = @At("TAIL"))
    private void addMiskStats(CallbackInfo info) {
        dataTracker.startTracking(SANITY, SANITY_CAP);
        dataTracker.startTracking(SHOCKED, false);
        dataTracker.startTracking(STAGE, 0);
        dataTracker.startTracking(POWER_POOL, 0);
        dataTracker.startTracking(MAX_SPELLS, 0);
        dataTracker.startTracking(AFFILIATION, Affiliation.NONE);
        dataTracker.startTracking(APPARENT_AFFILIATION, Affiliation.NONE);
    }

    @Override
    public int getSanity() {
        return dataTracker.get(SANITY);
    }

    @Override
    public void setSanity(int sanity, boolean ignoreFactors) {
        if (ignoreFactors || (!isShocked() && !hasStatusEffect(MMMiscRegistries.StatusEffects.TRANQUILIZED))) {
            dataTracker.set(SANITY, MathHelper.clamp(sanity, 0, getMaxSanity()));
        }
    }

    @Override
    public void setShocked(boolean shocked) {
        dataTracker.set(SHOCKED, shocked);
    }

    @Override
    public boolean isShocked() {
        return dataTracker.get(SHOCKED);
    }

    @Override
    public int getMaxSanity() {
        int mod = 0;
        for (Integer value : getSanityCapExpansions().values()) {
            mod += value;
        }
        return Constants.DataTrackers.SANITY_CAP + mod;
    }

    @Override
    public void addSanityCapExpansion(String name, int amount) {
        sanityCapOverrides.putIfAbsent(name, amount);
        if (!world.isClient) {
            ExpandSanityPacket.send((PlayerEntity) (Object) this, name, amount);
        }
        if (getSanity() > getMaxSanity()) setSanity(getMaxSanity(), true);
    }

    @Override
    public void removeSanityCapExpansion(String name) {
        sanityCapOverrides.remove(name);
        if (!world.isClient && sanityCapOverrides.containsKey(name)) {
            RemoveExpansionPacket.send((PlayerEntity) (Object) this, name);
        }
    }

    @Override
    public Map<String, Integer> getSanityCapExpansions() {
        return sanityCapOverrides;
    }

    @Override
    public void syncSanityData() {
        sanityCapOverrides.forEach((s, i) -> {
            PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
            data.writeString(s);
            data.writeInt(i);
            ExpandSanityPacket.send((PlayerEntity) (Object) this, s, i);
        });
    }

    @Inject(method = "writeCustomDataToTag(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void writeMiskData(CompoundTag compoundTag, CallbackInfo info) {
        CompoundTag tag = new CompoundTag();

        tag.putInt(Constants.NBT.SANITY, getSanity());
        tag.putBoolean(Constants.NBT.SHOCKED, isShocked());
        ListTag expansions = new ListTag();
        getSanityCapExpansions().forEach((s, i) -> {
            CompoundTag expansionTag = new CompoundTag();
            expansionTag.putString("Name", s);
            expansionTag.putInt("Amount", i);
            expansions.add(expansionTag);
        });
        tag.put(Constants.NBT.SANITY_EXPANSIONS, expansions);

        tag.putInt(Constants.NBT.POWER_POOL, getPowerPool());
        tag.putInt(Constants.NBT.MAX_SPELLS, getMaxSpells());

        CapabilityUtil.writeSpellData(this, tag);
        tag.putInt(Constants.NBT.ASCENSION_STAGE, getAscensionStage());
        tag.putString(Constants.NBT.AFFILIATION, getAffiliation(false).getId().toString());
        tag.putString(Constants.NBT.APPARENT_AFFILIATION, getAffiliation(true).getId().toString());

        CapabilityUtil.writeBlessingData(this, tag);
        compoundTag.put(Constants.NBT.MISK_DATA, tag);
    }

    @Inject(method = "readCustomDataFromTag(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    public void readMiskData(CompoundTag compoundTag, CallbackInfo info) {
        CompoundTag tag = (CompoundTag) compoundTag.get(Constants.NBT.MISK_DATA);
        if (tag != null) {
            syncSanityData();
            setSanity(tag.getInt(Constants.NBT.SANITY), true);
            setShocked(tag.getBoolean(Constants.NBT.SHOCKED));
            getSanityCapExpansions().clear();
            ((ListTag) tag.get(Constants.NBT.SANITY_EXPANSIONS)).forEach(s -> addSanityCapExpansion(((CompoundTag) s).getString("Name"), ((CompoundTag) s).getInt("Amount")));

            setPowerPool(tag.getInt(Constants.NBT.POWER_POOL));
            setMaxSpells(tag.getInt(Constants.NBT.MAX_SPELLS));

            syncSpellData();
            CapabilityUtil.readSpellData(this, tag);
            setAscensionStage(tag.getInt(Constants.NBT.ASCENSION_STAGE));

            setAffiliation(Affiliation.AFFILIATION_MAP.getOrDefault(new Identifier(tag.getString(Constants.NBT.AFFILIATION)), Affiliation.NONE), false);
            setAffiliation(Affiliation.AFFILIATION_MAP.getOrDefault(new Identifier(tag.getString(Constants.NBT.APPARENT_AFFILIATION)), Affiliation.NONE), true);
            CapabilityUtil.readBlessingData(this, tag);
        }
    }


    @Override
    public boolean isSupernatural() {
        return false;
    }

    @Override
    public Affiliation getAffiliation(boolean apparent) {
        return dataTracker.get(apparent ? APPARENT_AFFILIATION : AFFILIATION);
    }

    @Override
    public void setAffiliation(Affiliation affiliation, boolean apparent) {
        dataTracker.set(apparent ? APPARENT_AFFILIATION : AFFILIATION, affiliation);
    }

    @Override
    public int getMaxSpells() {
        return dataTracker.get(MAX_SPELLS);
    }

    @Override
    public void setMaxSpells(int amount) {
        dataTracker.set(MAX_SPELLS, amount);
    }

    @Override
    public int getPowerPool() {
        return dataTracker.get(POWER_POOL);
    }

    @Override
    public void setPowerPool(int amount) {
        dataTracker.set(POWER_POOL, amount);
    }

    @Override
    public List<Spell> getSpells() {
        return spells;
    }

    @Override
    public Set<SpellEffect> getLearnedEffects() {
        return learnedEffects;
    }

    @Override
    public Set<SpellMedium> getLearnedMediums() {
        return learnedMediums;
    }

    @Override
    public void learnEffect(SpellEffect effect) {
        learnedEffects.add(effect);
    }

    @Override
    public void learnMedium(SpellMedium medium) {
        learnedMediums.add(medium);
    }

    @Override
    public void syncSpellData() {
        if (!world.isClient) {
            SyncSpellCasterDataPacket.send(false, (PlayerEntity) (Object) this, this);
        }
    }

    @Override
    public int getAscensionStage() {
        return dataTracker.get(STAGE);
    }

    @Override
    public void setAscensionStage(int level) {
        dataTracker.set(STAGE, level);
    }

    @Override
    public boolean removeBlessing(Blessing blessing) {
        if (blessings.contains(blessing)) {
            blessing.onRemoved(this);
            return blessings.remove(blessing);
        }
        return false;
    }

    @Override
    public List<Blessing> getBlessings() {
        return blessings;
    }

    @Override
    public void addBlessing(Blessing blessing) {
        if (!blessings.contains(blessing) && blessings.size() < Constants.DataTrackers.MAX_BLESSINGS) {
            blessings.add(blessing);
            blessing.onAcquired(this);
        }
    }

    @Override
    public void syncBlessingData() {
        if (!world.isClient) {
            SyncBlessingsPacket.send((PlayerEntity) (Object) this, this);
        }
    }
}