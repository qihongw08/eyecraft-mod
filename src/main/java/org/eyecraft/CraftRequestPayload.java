package org.eyecraft;

import net.minecraft.item.Item;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public record CraftRequestPayload(RegistryEntry<Item> item) implements CustomPayload {
  public static final Identifier CRAFT_REQUEST_PAYLOAD_ID = Identifier.of("craft_request", "summon_lightning");
  public static final CustomPayload.Id<CraftRequestPayload> ID = new CustomPayload.Id<>(CRAFT_REQUEST_PAYLOAD_ID);
  public static final PacketCodec<RegistryByteBuf, CraftRequestPayload> CODEC = PacketCodec.tuple(
      Item.ENTRY_PACKET_CODEC, CraftRequestPayload::item, CraftRequestPayload::new);

  @Override
  public Id<? extends CustomPayload> getId() {
    return ID;
  }
}