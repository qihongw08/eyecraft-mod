package org.eyecraft.client;

import io.netty.buffer.Unpooled;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.eyecraft.CraftRequestPayload;

public class EyecraftClient implements ClientModInitializer {
  private volatile boolean isInventoryOpen = false;
  private volatile boolean isListening = false;

  // State variables controlled by Python
  private volatile boolean walking = false;
  private volatile boolean jumping = false;
  private volatile boolean leftClick = false;
  private volatile boolean rightClick = false;
  private volatile int lookingAt = 0;

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(this::handleCommands);
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      Screen current = MinecraftClient.getInstance().currentScreen;
      if (current instanceof InventoryScreen) {
        isInventoryOpen = true;
        clickSlot(10);
      }
    });

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.player != null) {
        if (client.currentScreen instanceof CraftingScreen || client.currentScreen instanceof InventoryScreen) {
          if (!isListening) {
            isListening = true;
            new Thread(() -> {
              String input = getSpeechInput(client);
              Item item = getItemFromString(input);
              if (item != null) {
                craftItemIfPossible(client, item);
              } else {
                client.player.sendMessage(Text.literal("item not found for: " + input), true);
              }
              isListening = false;
              client.execute(() -> client.setScreen(null));
            }).start();
          }
        } else {
          isListening = false;
        }
      }
    });

    PayloadTypeRegistry.playC2S().register(CraftRequestPayload.ID, CraftRequestPayload.CODEC);

    new Thread(this::startPythonListener, "PythonListener").start();
    new Thread(this::startVisionListener, "VisionListener").start();
  }

  public void requestCraftItem(Item item) {
    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
    buf.writeIdentifier(Registries.ITEM.getId(item));
    CraftRequestPayload payload = new CraftRequestPayload(RegistryEntry.of(item));
    ClientPlayNetworking.send(payload);
  }

  public Item getItemFromString(String name) {
    String idString = name.toLowerCase().replace(" ", "_");
    Identifier id = Identifier.tryParse(idString);

    if (!Registries.ITEM.containsId(id)) {
      return null;
    }
    return Registries.ITEM.get(id);
  }

  private void craftItemIfPossible(MinecraftClient mc, Item item) {
    if (mc.player == null) return;

    ClientRecipeBook recipeBook = mc.player.getRecipeBook();

    // 1. Find the recipe for the given item
    RecipeDisplayEntry targetEntry = null;
    for (RecipeResultCollection collection : recipeBook.getOrderedResults()) {
      for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
        ItemStack resultStack = entry.display().result().getFirst(null); // no context needed
        if (resultStack.getItem() == item) {
          targetEntry = entry;
          break;
        }
      }
      if (targetEntry != null) break;
    }

    if (targetEntry == null) return; // no recipe found

    // 2. Get ingredients
    List<Ingredient> ingredients = targetEntry.craftingRequirements().orElse(List.of());
    if (ingredients.isEmpty()) return;

    // 3. Check if the player has enough ingredients
    for (Ingredient ingredient : ingredients) {
      int required = 1; // each ingredient counts as 1 by default
      int countInInventory = 0;

      for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
        if (ingredient.test(stack)) {
          countInInventory += stack.getCount();
        }
      }

      if (countInInventory < required) {
        mc.player.sendMessage(Text.literal("missing \" + ingredients.size() + \" ingredients"), true);
        System.out.println("missing " + ingredients.size() + " ingredients");
        return;
      }
    }

    // 4. Remove the ingredients from inventory
    for (Ingredient ingredient : ingredients) {
      int remaining = 1;

      for (int i = 0; i < mc.player.getInventory().size(); i++) {
        ItemStack stack = mc.player.getInventory().getStack(i);
        if (ingredient.test(stack)) {
          int removed = Math.min(remaining, stack.getCount());
          stack.decrement(removed);
          remaining -= removed;
          if (remaining <= 0) break;
        }
      }
    }

    // 5. Give the resulting item to the player
    ItemStack resultStack = targetEntry.display().result().getFirst(null);
    mc.player.getInventory().insertStack(resultStack.copy());
  }

  private String getSpeechInput(MinecraftClient mc) {
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "python3",
          "/Users/qihongwu/Downloads/EyeCraft/scripts/stt.py");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      if (mc.player == null) return "";
      mc.player.sendMessage(Text.literal("Speak to craft item"), true);

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));

      StringBuilder output = new StringBuilder();
      String line;

      while ((line = reader.readLine()) != null) {
        output.append(line);
      }

      int exitCode = process.waitFor();

      if (exitCode == 0) {
        return output.toString().trim();
      } else {
        mc.player.sendMessage(Text.literal("Speech recognition failed"), true);
        System.out.println("Speech recognition process exited with code: " + exitCode);
        return "";
      }
    } catch (Exception e) {
      if (mc.player == null) return "";
      mc.player.sendMessage(Text.literal("Speech recognition failed"), true);
      System.out.println("Speech recognition error: " + e.getMessage());
      return "";
    }
  }

  private void handleCommands(MinecraftClient mc) {
    ClientPlayerEntity player = mc.player;
    if (player == null)
      return;

    if (walking)
      moveForward(player, 0.2);
    if (jumping && player.isOnGround())
      jump(player);

    switch (lookingAt) {
      case 1 -> {
        rotate(player, 5, 0);
      }
      case 2 -> {
        rotate(player, -5, 0);
      }
      case 3 -> {
        rotate(player, 0, -5);
      }
      case 4 -> {
        rotate(player, 0, 5);
      }
    }

    if (mc.interactionManager == null)
      return;

    if (leftClick) {
      if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
        mc.interactionManager.attackEntity(player, entityHit.getEntity());
      } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
        mc.interactionManager.updateBlockBreakingProgress(blockHit.getBlockPos(), blockHit.getSide());
        player.swingHand(Hand.MAIN_HAND);
      } else {
        player.swingHand(Hand.MAIN_HAND);
      }
    }

    if (rightClick) {
      // Get item in hand
      ItemStack stack = player.getMainHandStack();

      if (stack.contains(DataComponentTypes.CONSUMABLE)) {
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
      } else if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
        mc.interactionManager.interactEntity(player, entityHit.getEntity(), Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
      } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
        player.swingHand(Hand.MAIN_HAND);
      } else {
        player.setCurrentHand(Hand.MAIN_HAND);
        mc.options.useKey.setPressed(true);
      }
    } else {
      mc.options.useKey.setPressed(false);
    }

    jumping = false;
  }

  private void moveForward(ClientPlayerEntity player, double speed) {
    double yawRad = Math.toRadians(player.getYaw());
    double currentYVel = player.getVelocity().y;
    Vec3d vec3d = new Vec3d(
        -Math.sin(yawRad) * speed,
        currentYVel,
        Math.cos(yawRad) * speed);
    player.setVelocity(vec3d);
  }

  private void strafe(ClientPlayerEntity player, double speed) {
    double yawRad = Math.toRadians(player.getYaw());
    double currentYVel = player.getVelocity().y;
    Vec3d vec3d = new Vec3d(
        Math.cos(yawRad) * speed,
        currentYVel,
        Math.sin(yawRad) * speed);
    player.setVelocity(vec3d);
  }

  private void jump(ClientPlayerEntity player) {
    Vec3d currentVel = player.getVelocity();
    Vec3d vec3d = new Vec3d(currentVel.x, 0.42, currentVel.z);
    player.setVelocity(vec3d);
  }

  private void clickSlot(int slotId) {
    MinecraftClient client = MinecraftClient.getInstance();
    ClientPlayerEntity player = client.player;

    if (player != null && player.currentScreenHandler != null) {
      client.interactionManager.clickSlot(
          player.currentScreenHandler.syncId, // the container ID
          slotId, // the slot index
          0, // mouse button (0 = left, 1 = right)
          SlotActionType.PICKUP, // action type (PICKUP, QUICK_MOVE, etc.)
          player);
    }
  }

  private void rotate(ClientPlayerEntity player, float deltaYaw, float deltaPitch) {
    float newYaw = player.getYaw() + deltaYaw;
    player.setYaw(newYaw);
    player.setHeadYaw(newYaw);

    float newPitch = player.getPitch() + deltaPitch;
    newPitch = Math.max(-90.0f, Math.min(90.0f, newPitch));
    player.setPitch(newPitch);
  }

  private void startPythonListener() {
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "/Users/qihongwu/eyecraft/.venv/bin/python3.12",
          "/Users/qihongwu/eyecraft/message.py");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));

      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length == 6) {
          leftClick = parts[0].equalsIgnoreCase("True");
          rightClick = parts[1].equalsIgnoreCase("True");
          jumping = parts[2].equalsIgnoreCase("True");
          walking = parts[3].equalsIgnoreCase("True");
        }
      }

      process.waitFor();
    } catch (Exception e) {
      System.out.println("Python listener error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void startVisionListener() {
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "python3",
          "/Users/tomasdavola/IdeaProjects/eyecraft-mod1/scripts/live.py");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));

      String line;
      while ((line = reader.readLine()) != null) {
        int firstNumber = Character.getNumericValue(line.charAt(0));
        lookingAt = firstNumber;
      }

      process.waitFor();
    } catch (Exception e) {
      System.out.println("Vision listener error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}