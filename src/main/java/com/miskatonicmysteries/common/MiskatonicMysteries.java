package com.miskatonicmysteries.common;

import com.miskatonicmysteries.common.feature.ModCommand;
import com.miskatonicmysteries.common.handler.SchedulingHandler;
import com.miskatonicmysteries.common.handler.networking.packet.SpellPacket;
import com.miskatonicmysteries.common.handler.networking.packet.c2s.SyncConfigurationPacket;
import com.miskatonicmysteries.common.handler.networking.packet.SyncSpellCasterDataPacket;
import com.miskatonicmysteries.common.handler.networking.packet.c2s.ClientRiteInputPacket;
import com.miskatonicmysteries.common.handler.networking.packet.c2s.InvokeManiaPacket;
import com.miskatonicmysteries.common.registry.MMAffiliations;
import com.miskatonicmysteries.common.registry.MMBlessings;
import com.miskatonicmysteries.common.registry.MMCriteria;
import com.miskatonicmysteries.common.registry.MMEntities;
import com.miskatonicmysteries.common.registry.MMInsanity;
import com.miskatonicmysteries.common.registry.MMLootTables;
import com.miskatonicmysteries.common.registry.MMObjects;
import com.miskatonicmysteries.common.registry.MMParticles;
import com.miskatonicmysteries.common.registry.MMRecipes;
import com.miskatonicmysteries.common.registry.MMRites;
import com.miskatonicmysteries.common.registry.MMSounds;
import com.miskatonicmysteries.common.registry.MMSpellEffects;
import com.miskatonicmysteries.common.registry.MMSpellMediums;
import com.miskatonicmysteries.common.registry.MMStatusEffects;
import com.miskatonicmysteries.common.registry.MMTrades;
import com.miskatonicmysteries.common.registry.MMWorld;

import com.miskatonicmysteries.common.util.Constants;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.item.FishingRodItem;

import me.shedaniel.autoconfig.example.ExampleInits;
import software.bernie.example.GeckoLibMod;
import software.bernie.example.client.renderer.entity.ExampleGeoRenderer;
import software.bernie.geckolib3.GeckoLib;

public class MiskatonicMysteries implements ModInitializer {

	@Override
	public void onInitialize() {
		MMMidnightLibConfig.init(Constants.MOD_ID, MMMidnightLibConfig.class);
		GeckoLibMod.DISABLE_IN_DEV = true;
		MMAffiliations.init();
		MMBlessings.init();
		MMObjects.init();
		MMEntities.init();
		MMSpellMediums.init();
		MMSpellEffects.init();
		MMRites.init();
		MMSounds.init();
		MMStatusEffects.init();
		MMLootTables.init();
		MMTrades.init();
		MMCriteria.init();
		MMRecipes.init();
		MMInsanity.init();
		MMParticles.init();
		ModCommand.setup();
		MMWorld.init();
		registerPackets();
		SchedulingHandler.init();
		MMServerEvents.init();
	}

	private void registerPackets() {
		ServerPlayNetworking.registerGlobalReceiver(InvokeManiaPacket.ID, InvokeManiaPacket::handle);
		ServerPlayNetworking.registerGlobalReceiver(SyncSpellCasterDataPacket.ID, SyncSpellCasterDataPacket::handleFromClient);
		ServerPlayNetworking.registerGlobalReceiver(SpellPacket.ID, SpellPacket::handleFromClient);
		ServerPlayNetworking.registerGlobalReceiver(ClientRiteInputPacket.ID, ClientRiteInputPacket::handle);
		ServerPlayNetworking.registerGlobalReceiver(SyncConfigurationPacket.ID, SyncConfigurationPacket::handleFromClient);
	}
}
