package ru.holyworld.cannonmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

public class AutoTntCannonMod implements ClientModInitializer {
    private static KeyBinding triggerKey;
    private static int state = 0; // Фазы: 0 - ожидание, 1 - установка, 2 - загрузка, 3 - активация
    private static int ticksDelay = 0;
    private static BlockPos placedPos = null;

    @Override
    public void onInitializeClient() {
        triggerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cannonmod.fire",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.cannonmod.title"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (triggerKey.wasPressed() && state == 0) {
                state = 1;
                ticksDelay = 0;
            }

            if (state > 0) {
                handleMacroStep(client);
            }
        });
    }

    private static void handleMacroStep(MinecraftClient client) {
        if (ticksDelay > 0) {
            ticksDelay--;
            return;
        }

        switch (state) {
            case 1: { // Шаг 1: Ставим Тнт-Пушку
                int cannonSlot = findItemSlot(client, "тнт-пушка");
                if (cannonSlot == -1) {
                    client.player.sendMessage(new net.minecraft.text.LiteralText("§cТнт-пушка не найдена в хотбаре!"), true);
                    state = 0;
                    return;
                }

                if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
                    client.player.inventory.selectedSlot = cannonSlot;
                    
                    // Ставим пушку
                    client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND, hit);
                    placedPos = hit.getBlockPos().offset(hit.getSide());

                    // Кликаем по ней для открытия меню
                    BlockHitResult openHit = new BlockHitResult(hit.getPos(), hit.getSide(), placedPos, false);
                    client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND, openHit);

                    state = 2;
                    ticksDelay = 2; // Задержка ответа сервера на открытие GUI
                } else {
                    state = 0;
                }
                break;
            }

            case 2: { // Шаг 2: Кладём Динамит B
                if (client.player.currentScreenHandler != null && client.currentScreen != null) {
                    int dynSlot = findInventorySlot(client, "динамит b");
                    if (dynSlot == -1) dynSlot = findInventorySlot(client, "динамит б");

                    if (dynSlot != -1) {
                        // Перемещаем динамит в слот пушки (Shift-Click или клик)
                        client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                dynSlot,
                                0,
                                SlotActionType.QUICK_MOVE,
                                client.player
                        );
                    }
                    client.player.closeHandledScreen();
                    state = 3;
                    ticksDelay = 1;
                }
                break;
            }

            case 3: { // Шаг 3: Ставим редстоун-блок впритык
                int redstoneSlot = findHotbarItem(client, Items.REDSTONE_BLOCK);
                if (redstoneSlot != -1 && placedPos != null) {
                    client.player.inventory.selectedSlot = redstoneSlot;
                    
                    // Ставим редстоун сбоку от пушки
                    BlockHitResult redstoneHit = new BlockHitResult(
                            client.player.getPos(),
                            Direction.UP,
                            placedPos.offset(Direction.NORTH),
                            false
                    );
                    client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND, redstoneHit);
                }
                state = 0; // Завершение цикла
                break;
            }
        }
    }

    private static int findItemSlot(MinecraftClient client, String textInName) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.inventory.getStack(i);
            if (!stack.isEmpty() && stack.getName().getString().toLowerCase().contains(textInName.toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    private static int findInventorySlot(MinecraftClient client, String textInName) {
        for (int i = 0; i < client.player.currentScreenHandler.slots.size(); i++) {
            ItemStack stack = client.player.currentScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getName().getString().toLowerCase().contains(textInName.toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    private static int findHotbarItem(MinecraftClient client, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (client.player.inventory.getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }
}
