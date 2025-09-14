package org.eyecraft.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class EyecraftClient implements ClientModInitializer {
  private boolean isInventoryOpen = false;

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
    // Optional test commands
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
      dispatcher.register(ClientCommandManager.literal("testforward")
          .executes(context -> {
            walking = true;
            return 1;
          }));
      dispatcher.register(ClientCommandManager.literal("jump")
          .executes(context -> {
            jumping = true;
            return 1;
          }));
      dispatcher.register(ClientCommandManager.literal("checkinv")
          .executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Screen current = client.currentScreen;
            if (current instanceof HandledScreen<?>) {
              System.out.println("Inventory (or container) is open!");
            } else {
              System.out.println("No inventory open.");
            }
            return 1;
          }));
    });

    new Thread(this::startPythonListener, "PythonListener").start();
    new Thread(this::startVisionListener, "VisionListener").start();
  }

  private void handleCommands(MinecraftClient mc) {
    ClientPlayerEntity player = mc.player;
    if (player == null) return;

    if (walking) moveForward(player, 0.2);
    if (jumping && player.isOnGround()) jump(player);

    switch (lookingAt){
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

    if (mc.interactionManager == null) return;

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
        Math.cos(yawRad) * speed
    );
    player.setVelocity(vec3d);
  }

  private void strafe(ClientPlayerEntity player, double speed) {
    double yawRad = Math.toRadians(player.getYaw());
    double currentYVel = player.getVelocity().y;
    Vec3d vec3d = new Vec3d(
        Math.cos(yawRad) * speed,
        currentYVel,
        Math.sin(yawRad) * speed
    );
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
              slotId,                             // the slot index
              0,                                  // mouse button (0 = left, 1 = right)
              SlotActionType.PICKUP,              // action type (PICKUP, QUICK_MOVE, etc.)
              player
      );
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
          "/Users/qihongwu/eyecraft/message.py"
      );
      pb.redirectErrorStream(true);
      Process process = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream())
      );

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
              "/Users/tomasdavola/IdeaProjects/eyecraft-mod1/scripts/live.py"
      );
      pb.redirectErrorStream(true);
      Process process = pb.start();

      BufferedReader reader = new BufferedReader(
              new InputStreamReader(process.getInputStream())
      );

      String line;
      while ((line = reader.readLine()) != null) {
        int firstNumber = Character.getNumericValue(line.charAt(0));
        lookingAt=firstNumber;
      }


      process.waitFor();
    } catch (Exception e) {
      System.out.println("Vision listener error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}