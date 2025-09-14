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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

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
              System.out.println(input);
              Item item = getItemFromString(input);
              if (item != null) {
                handleCrafting(client, item);
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

    new Thread(this::startPythonListener, "PythonListener").start();
    new Thread(this::startVisionListener, "VisionListener").start();
  }

  public Item getItemFromString(String name) {
    String idString = name.toLowerCase().replace(" ", "_");
    Identifier id = Identifier.tryParse(idString);

    if (!Registries.ITEM.containsId(id)) {
      return null;
    }
    return Registries.ITEM.get(id);
  }

  private void handleCrafting(MinecraftClient mc, Item item) {
    if (mc.player == null || mc.interactionManager == null) return;

    if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)
        && !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return; // must be in 3x3; inventory screen works for 2x2

    NetworkRecipeId netId = findCraftableByOutput(mc, item);
    if (netId == null) return;

    int syncId = mc.player.currentScreenHandler.syncId;
    mc.interactionManager.clickRecipe(syncId, netId, false);
    Slot outputSlot = ((AbstractCraftingScreenHandler) mc.player.currentScreenHandler).getOutputSlot();
    mc.interactionManager.clickSlot(syncId, outputSlot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
  }

  // Returns a craftable NetworkRecipeId for a given output Item, or null if none.
  public static NetworkRecipeId findCraftableByOutput(MinecraftClient mc, Item target) {
    ClientRecipeBook book = mc.player.getRecipeBook(); // visible in UI, includes ordered results
    List<RecipeResultCollection> groups = book.getOrderedResults(); // what the book renders
    RecipeFinder finder = fromPlayerInventory(mc);

    for (RecipeResultCollection group : groups) {
      // Optionally refresh craftable/displayable flags against current inventory
      group.populateRecipes(finder, display -> true);

      for (var entry : group.getAllRecipes()) {
        // Each entry carries a runtime NetworkRecipeId and a display describing output
        var stacks = entry.getStacks(RecipeContexts.EMPTY_CTX); // output/preview stacks
        boolean matchesOutput = !stacks.isEmpty() && stacks.getFirst().getItem() == target;
        if (!matchesOutput) continue;

        NetworkRecipeId netId = entry.id();
        if (group.isCraftable(netId)) {
          return netId;
        }
      }
    }
    return null;
  }

  private static RecipeFinder fromPlayerInventory(MinecraftClient mc) {
    RecipeFinder finder = new RecipeFinder();
    var inv = mc.player.getInventory();
    for (int i = 0; i < inv.size(); i++) {
      ItemStack stack = inv.getStack(i);
      if (!stack.isEmpty()) {
        finder.addInputIfUsable(stack);
      }
    }
    return finder;
  }

  private String getSpeechInput(MinecraftClient mc) {
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "python3",
          "/Users/qihongwu/Downloads/EyeCraft/scripts/stt.py");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      if (mc.player == null) return "";

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
        rotate(player, 3, 0);
      }
      case 2 -> {
        rotate(player, -3, 0);
      }
      case 3 -> {
        rotate(player, 0, -3);
      }
      case 4 -> {
        rotate(player, 0, 3);
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
    mc.options.useKey.setPressed(rightClick);

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