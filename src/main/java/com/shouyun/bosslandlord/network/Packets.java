package com.shouyun.bosslandlord.network;

import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.server.MatchManager;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Packets {
    public static final int START = 0, PLAY = 1, PASS = 2, LEAVE = 3, REOPEN = 4;
    public static final int CLAIM = 5, DECLINE = 6, NORMAL = 7, DOUBLE = 8, SUPER = 9;
    public static final int PICK_GUEST = 10, INVITE_PLAYER = 11, CANCEL_LOBBY = 12;
    public static final int DUEL_PLAYER = 13, REMATCH = 14, TABLE_PROP = 15;
    public static final UUID NONE = new UUID(0, 0);
    // Installed exclusively by the physical-client initializer. No client class loading on a server.
    public static Consumer<CompoundTag> clientReceiver = tag -> {};
    private Packets() {}
    public record Action(int action, UUID match, int revision, int[] cards) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = new StreamCodec<>() {
            @Override public Action decode(RegistryFriendlyByteBuf b) { return new Action(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarIntArray(20)); }
            @Override public void encode(RegistryFriendlyByteBuf b, Action p) { b.writeVarInt(p.action); b.writeUUID(p.match); b.writeVarInt(p.revision); b.writeVarIntArray(p.cards); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record State(CompoundTag data) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            @Override public State decode(RegistryFriendlyByteBuf b) { CompoundTag n = b.readNbt(); return new State(n == null ? new CompoundTag() : n); }
            @Override public void encode(RegistryFriendlyByteBuf b, State p) { b.writeNbt(p.data); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("7");
        registrar.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) MatchManager.action(player, p);
        });
        registrar.playToClient(State.TYPE, State.CODEC, (p, ctx) -> clientReceiver.accept(p.data));
    }
    public static void send(ServerPlayer player, CompoundTag tag) { PacketDistributor.sendToPlayer(player, new State(tag)); }
    public static void request(int action, UUID match, int revision, int[] cards) {
        PacketDistributor.sendToServer(new Action(action, match, revision, cards));
    }
}
