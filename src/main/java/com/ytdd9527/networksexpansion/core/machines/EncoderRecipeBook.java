package com.ytdd9527.networksexpansion.core.machines;

import com.balugaq.netex.utils.Lang;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.guizhanss.minecraft.guizhanlib.gugu.minecraft.helpers.inventory.ItemStackHelper;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recipe book opened from slot 4 of a {@link BlueprintEncoder}.
 * <p>
 * Lists the recipes the encoder supports in a Slimefun-guide-like menu. Left click previews a recipe,
 * right click copies it into the encoder: the ingredients are taken from the encoder's network
 * (quantum storages, drawers, cells, ...). Missing ingredients are reported and their slots stay empty.
 */
public final class EncoderRecipeBook {
    private static final int BACK_SLOT = 1;
    private static final int INFO_SLOT = 4;
    private static final int SEARCH_SLOT = 7;
    private static final int PREV_SLOT = 46;
    private static final int NEXT_SLOT = 52;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_SIZE = 36;

    private static final int[] PREVIEW_GRID = new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int PREVIEW_ARROW = 23;
    private static final int PREVIEW_OUTPUT = 25;
    private static final int PREVIEW_COPY = 43;

    private EncoderRecipeBook() {
    }

    /**
     * Opens the recipe list of an encoder.
     */
    @SuppressWarnings("deprecation")
    public static void open(
        @NotNull Player player,
        @NotNull BlockMenu encoderMenu,
        @NotNull BlueprintEncoder encoder,
        @Nullable String filter,
        int page) {
        final List<Map.Entry<ItemStack[], ItemStack>> recipes = getRecipes(encoder, filter);
        final int pages = Math.max(1, (recipes.size() - 1) / CONTENT_SIZE + 1);
        final int current = Math.min(Math.max(page, 1), pages);

        final ChestMenu menu = new ChestMenu(Lang.getString("messages.encoder-book.title"));
        menu.setSize(54);
        menu.setEmptySlotsClickable(false);
        menu.setPlayerInventoryClickable(false);
        menu.addMenuOpeningHandler(p -> p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1));

        for (int slot = 0; slot < 54; slot++) {
            if (slot < CONTENT_START || slot >= CONTENT_START + CONTENT_SIZE) {
                menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }
        }

        menu.addItem(BACK_SLOT, ChestMenuUtils.getBackButton(player), (p, s, item, action) -> {
            encoderMenu.open(p);
            return false;
        });

        menu.addItem(INFO_SLOT, createItem(
            Material.KNOWLEDGE_BOOK,
            Lang.getString("messages.encoder-book.info-name"),
            formatLore("messages.encoder-book.info-lore",
                encoder.craftType().translate(),
                recipes.size(),
                filter == null ? Lang.getString("messages.encoder-book.no-filter") : filter)),
            ChestMenuUtils.getEmptyClickHandler());

        menu.addItem(SEARCH_SLOT, createItem(
            Material.NAME_TAG,
            Lang.getString("messages.encoder-book.search-name"),
            Lang.getStringList("messages.encoder-book.search-lore")), (p, s, item, action) -> {
            if (action.isRightClicked()) {
                open(p, encoderMenu, encoder, null, 1);
                return false;
            }
            p.closeInventory();
            p.sendMessage(Lang.getString("messages.encoder-book.search-prompt"));
            ChatUtils.awaitInput(p, input -> {
                String query = ChatColor.stripColor(input).trim();
                open(p, encoderMenu, encoder, query.isEmpty() ? null : query, 1);
            });
            return false;
        });

        menu.addItem(PREV_SLOT, ChestMenuUtils.getPreviousButton(player, current, pages), (p, s, item, action) -> {
            if (current > 1) {
                open(p, encoderMenu, encoder, filter, current - 1);
            }
            return false;
        });
        menu.addItem(NEXT_SLOT, ChestMenuUtils.getNextButton(player, current, pages), (p, s, item, action) -> {
            if (current < pages) {
                open(p, encoderMenu, encoder, filter, current + 1);
            }
            return false;
        });

        if (recipes.isEmpty()) {
            menu.addItem(CONTENT_START + 13, createItem(
                Material.BARRIER, Lang.getString("messages.encoder-book.empty"), List.of()),
                ChestMenuUtils.getEmptyClickHandler());
        }

        final int start = (current - 1) * CONTENT_SIZE;
        for (int i = 0; i < CONTENT_SIZE; i++) {
            final int slot = CONTENT_START + i;
            final int index = start + i;
            if (index >= recipes.size()) {
                continue;
            }
            final Map.Entry<ItemStack[], ItemStack> recipe = recipes.get(index);
            menu.addItem(slot, withLore(recipe.getValue(), Lang.getStringList("messages.encoder-book.recipe-lore")),
                (p, s, item, action) -> {
                    if (action.isRightClicked()) {
                        copyToEncoder(p, encoderMenu, recipe.getKey(), action.isShiftClicked());
                    } else {
                        openPreview(p, encoderMenu, encoder, recipe, filter, current);
                    }
                    return false;
                });
        }

        menu.open(player);
    }

    @SuppressWarnings("deprecation")
    private static void openPreview(
        @NotNull Player player,
        @NotNull BlockMenu encoderMenu,
        @NotNull BlueprintEncoder encoder,
        Map.@NotNull Entry<ItemStack[], ItemStack> recipe,
        @Nullable String filter,
        int page) {
        final ChestMenu menu = new ChestMenu(Lang.getString("messages.encoder-book.preview-title"));
        menu.setSize(54);
        menu.setEmptySlotsClickable(false);
        menu.setPlayerInventoryClickable(false);

        final Set<Integer> grid = new HashSet<>();
        for (int slot : PREVIEW_GRID) {
            grid.add(slot);
        }
        for (int slot = 0; slot < 54; slot++) {
            if (!grid.contains(slot)) {
                menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }
        }

        menu.addItem(BACK_SLOT, ChestMenuUtils.getBackButton(player), (p, s, item, action) -> {
            open(p, encoderMenu, encoder, filter, page);
            return false;
        });

        final ItemStack[] inputs = recipe.getKey();
        for (int i = 0; i < PREVIEW_GRID.length; i++) {
            ItemStack input = i < inputs.length ? inputs[i] : null;
            if (input != null && input.getType() != Material.AIR) {
                menu.addItem(PREVIEW_GRID[i], input.clone(), ChestMenuUtils.getEmptyClickHandler());
            }
        }

        menu.addItem(PREVIEW_ARROW, createItem(Material.SPECTRAL_ARROW, " ", List.of()),
            ChestMenuUtils.getEmptyClickHandler());
        menu.addItem(PREVIEW_OUTPUT, recipe.getValue().clone(), ChestMenuUtils.getEmptyClickHandler());

        menu.addItem(PREVIEW_COPY, createItem(
            Material.LIME_STAINED_GLASS_PANE,
            Lang.getString("messages.encoder-book.copy-name"),
            Lang.getStringList("messages.encoder-book.copy-lore")), (p, s, item, action) -> {
            copyToEncoder(p, encoderMenu, inputs, action.isShiftClicked());
            return false;
        });

        menu.open(player);
    }

    /**
     * Copies a recipe into the encoder's recipe slots, taking the ingredients from its network.
     *
     * @param fullStack fill every used slot with a full stack instead of the recipe amount
     */
    private static void copyToEncoder(
        @NotNull Player player, @NotNull BlockMenu encoderMenu, ItemStack @NotNull [] inputs, boolean fullStack) {
        final Location location = encoderMenu.getLocation();
        if (StorageCacheUtils.getMenu(location) != encoderMenu) {
            player.closeInventory();
            player.sendMessage(Lang.getString("messages.encoder-book.encoder-gone"));
            return;
        }

        final NodeDefinition definition = NetworkStorage.getNode(location);
        if (definition == null || definition.getNode() == null) {
            player.sendMessage(Lang.getString("messages.encoder-book.not-connected"));
            return;
        }
        final NetworkRoot root = definition.getNode().getRoot();

        // Return whatever is in the recipe slots to the network first
        boolean blocked = false;
        for (int slot : BlueprintEncoder.RECIPE_SLOTS) {
            ItemStack existing = encoderMenu.getItemInSlot(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                continue;
            }
            ItemStack remaining = existing.clone();
            root.addItemStack0(location, remaining);
            if (remaining.getAmount() > 0) {
                encoderMenu.replaceExistingItem(slot, remaining);
                blocked = true;
            } else {
                encoderMenu.replaceExistingItem(slot, null);
            }
        }
        if (blocked) {
            player.sendMessage(Lang.getString("messages.encoder-book.slots-not-empty"));
            return;
        }

        final Map<String, Integer> missing = new LinkedHashMap<>();
        for (int i = 0; i < BlueprintEncoder.RECIPE_SLOTS.length && i < inputs.length; i++) {
            ItemStack input = inputs[i];
            if (input == null || input.getType() == Material.AIR) {
                continue;
            }
            int wanted = fullStack ? input.getMaxStackSize() : input.getAmount();
            ItemStack taken = root.getItemStack0(location, new ItemRequest(input.clone(), wanted));
            int got = taken == null ? 0 : taken.getAmount();
            if (got > 0) {
                encoderMenu.replaceExistingItem(BlueprintEncoder.RECIPE_SLOTS[i], taken);
            }
            if (got < wanted) {
                missing.merge(ItemStackHelper.getDisplayName(input), wanted - got, Integer::sum);
            }
        }

        if (missing.isEmpty()) {
            player.sendMessage(Lang.getString("messages.encoder-book.copied"));
        } else {
            player.sendMessage(Lang.getString("messages.encoder-book.partial"));
            missing.forEach((name, amount) -> player.sendMessage(
                String.format(Lang.getString("messages.encoder-book.missing-entry"), name, amount)));
        }
        encoderMenu.open(player);
    }

    private static @NotNull List<Map.Entry<ItemStack[], ItemStack>> getRecipes(
        @NotNull BlueprintEncoder encoder, @Nullable String filter) {
        final String query = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        final List<Map.Entry<ItemStack[], ItemStack>> result = new ArrayList<>();
        for (Map.Entry<ItemStack[], ItemStack> entry : encoder.getRecipeEntries()) {
            ItemStack output = entry.getValue();
            if (entry.getKey() == null || output == null || output.getType() == Material.AIR) {
                continue;
            }
            if (query != null && !plainName(output).toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(entry);
        }
        result.sort(Comparator.comparing(entry -> plainName(entry.getValue()).toLowerCase(Locale.ROOT)));
        return result;
    }

    private static @NotNull String plainName(@NotNull ItemStack itemStack) {
        String name = ChatColor.stripColor(ItemStackHelper.getDisplayName(itemStack));
        return name == null ? "" : name;
    }

    @SuppressWarnings("deprecation")
    private static @NotNull ItemStack createItem(
        @NotNull Material material, @NotNull String name, @NotNull List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    @SuppressWarnings("deprecation")
    private static @NotNull ItemStack withLore(@NotNull ItemStack itemStack, @NotNull List<String> extra) {
        ItemStack clone = itemStack.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.addAll(extra);
            meta.setLore(lore);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private static @NotNull List<String> formatLore(@NotNull String key, Object @NotNull ... args) {
        List<String> lore = new ArrayList<>();
        int index = 0;
        for (String line : Lang.getStringList(key)) {
            if (line.contains("%s") && index < args.length) {
                lore.add(String.format(line, args[index++]));
            } else {
                lore.add(line);
            }
        }
        return lore;
    }
}
