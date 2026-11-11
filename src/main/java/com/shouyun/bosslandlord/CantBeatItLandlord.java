package com.shouyun.bosslandlord;

import com.mojang.logging.LogUtils;
import com.shouyun.bosslandlord.network.Packets;
import com.shouyun.bosslandlord.server.MatchManager;
import com.shouyun.bosslandlord.world.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(CantBeatItLandlord.MODID)
public final class CantBeatItLandlord {
    public static final String MODID = "bosslandlord";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    // These models are drawn by temporary entities; no world blocks are replaced during a match.
    public static final DeferredBlock<Block> TABLE_MODEL = BLOCKS.registerSimpleBlock("card_table", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).noOcclusion());
    public static final DeferredBlock<Block> CHAIR_MODEL = BLOCKS.registerSimpleBlock("card_chair", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).noOcclusion());
    public static final DeferredItem<ChallengeItem> CARDS = ITEMS.register("challenge_cards", () -> new ChallengeItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<EntityType<?>, EntityType<TableEntity>> TABLE = ENTITIES.register("card_table", () -> EntityType.Builder.<TableEntity>of(TableEntity::new, MobCategory.MISC).sized(2.5F, 1F).clientTrackingRange(12).updateInterval(5).noSave().build("bosslandlord:card_table"));
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.register("card_seat", () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(.2F, .2F).clientTrackingRange(12).updateInterval(5).noSave().build("bosslandlord:card_seat"));
    public static final DeferredHolder<EntityType<?>, EntityType<GuestEntity>> GUEST = ENTITIES.register("card_guest",
            () -> EntityType.Builder.of(GuestEntity::new, MobCategory.MISC).sized(1.15F, 2.18F)
                    .clientTrackingRange(12).noSave().build("bosslandlord:card_guest"));
    public CantBeatItLandlord(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        MatchSounds.SOUNDS.register(bus);
        bus.addListener(Packets::register);
        bus.addListener(CantBeatItLandlord::creative);
        bus.addListener(CantBeatItLandlord::attributes);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(MatchManager.class);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(CARDS);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GUEST.get(), Mob.createMobAttributes().build());
    }
}
