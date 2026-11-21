package com.balugaq.netex.api.enums;

import com.balugaq.netex.api.helpers.SupportedAncientAltarRecipes;
import com.balugaq.netex.api.helpers.SupportedArmorForgeRecipes;
import com.balugaq.netex.api.helpers.SupportedCompressorRecipes;
import com.balugaq.netex.api.helpers.SupportedCraftingTableRecipes;
import com.balugaq.netex.api.helpers.SupportedExpansionWorkbenchRecipes;
import com.balugaq.netex.api.helpers.SupportedGrindStoneRecipes;
import com.balugaq.netex.api.helpers.SupportedJuicerRecipes;
import com.balugaq.netex.api.helpers.SupportedMagicWorkbenchRecipes;
import com.balugaq.netex.api.helpers.SupportedOreCrusherRecipes;
import com.balugaq.netex.api.helpers.SupportedPressureChamberRecipes;
import com.balugaq.netex.api.helpers.SupportedQuantumWorkbenchRecipes;
import com.balugaq.netex.api.helpers.SupportedSmelteryRecipes;
import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.core.items.Blueprint;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * @author balugaq
 */
@NullMarked
public enum CraftType {
    ANCIENT_ALTAR(SupportedAncientAltarRecipes::getRecipes, SupportedAncientAltarRecipes::testRecipe),
    ARMOR_FORGE(SupportedArmorForgeRecipes::getRecipes, SupportedArmorForgeRecipes::testRecipe),
    COMPRESSOR(SupportedCompressorRecipes::getRecipes, SupportedCompressorRecipes::testRecipe),
    CRAFTING(SupportedCraftingTableRecipes::getRecipes, SupportedCraftingTableRecipes::testRecipe),
    EXPANSION_WORKBENCH(SupportedExpansionWorkbenchRecipes::getRecipes, SupportedExpansionWorkbenchRecipes::testRecipe),
    GRIND_STONE(SupportedGrindStoneRecipes::getRecipes, SupportedGrindStoneRecipes::testRecipe),
    JUICER(SupportedJuicerRecipes::getRecipes, SupportedJuicerRecipes::testRecipe),
    MAGIC_WORKBENCH(SupportedMagicWorkbenchRecipes::getRecipes, SupportedMagicWorkbenchRecipes::testRecipe),
    ORE_CRUSHER(SupportedOreCrusherRecipes::getRecipes, SupportedOreCrusherRecipes::testRecipe),
    PRESSURE_CHAMBER(SupportedPressureChamberRecipes::getRecipes, SupportedPressureChamberRecipes::testRecipe),
    QUANTUM_WORKBENCH(SupportedQuantumWorkbenchRecipes::getRecipes, SupportedQuantumWorkbenchRecipes::testRecipe),
    SMELTERY(SupportedSmelteryRecipes::getRecipes, SupportedSmelteryRecipes::testRecipe);

    // Built lazily: the Supported*Recipes classes scan the Slimefun registry when first loaded, so
    // resolving them while Networks itself is enabling would miss recipes of addons enabled later.
    private static volatile Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> map;

    private final Supplier<Map<ItemStack[], ItemStack>> recipeSupplier;
    private final BiPredicate<ItemStack[], ItemStack[]> testRecipe;
    private volatile Set<Map.Entry<ItemStack[], ItemStack>> recipeEntries;

    CraftType(Supplier<Map<ItemStack[], ItemStack>> recipeSupplier, BiPredicate<ItemStack[], ItemStack[]> testRecipe) {
        this.recipeSupplier = recipeSupplier;
        this.testRecipe = testRecipe;
    }

    private static Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> buildMap() {
        Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> result = new HashMap<>();
        for (CraftType type : values()) {
            result.put(type, type.getRecipeEntries());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public Set<Map.Entry<ItemStack[], ItemStack>> getRecipeEntries() {
        Set<Map.Entry<ItemStack[], ItemStack>> entries = this.recipeEntries;
        if (entries == null) {
            synchronized (this) {
                entries = this.recipeEntries;
                if (entries == null) {
                    entries = this.recipeSupplier.get().entrySet();
                    this.recipeEntries = entries;
                }
            }
        }
        return entries;
    }

    public boolean testRecipe(ItemStack[] inputs, ItemStack[] recipe) {
        return testRecipe.test(inputs, recipe);
    }

    public boolean isValidBlueprint(SlimefunItem item) {
        if (item instanceof Blueprint blueprint) {
            return blueprint.craftType() == this;
        }
        return false;
    }

    public void blueprintSetter(ItemStack itemStack, ItemStack[] inputs, ItemStack crafted) {
        Blueprint.setBlueprint(itemStack, inputs, crafted);
    }

    public String translate() {
        return Lang.getString("messages.normal-operation.common.crafter_types." + this.name().toLowerCase());
    }

    @FunctionalInterface
    public interface BlueprintSetter {
        void apply(ItemStack itemStack, ItemStack[] inputs, ItemStack crafted);
    }

    public static Collection<Set<Map.Entry<ItemStack[], ItemStack>>> entries() {
        return Arrays.stream(values()).map(CraftType::getRecipeEntries).toList();
    }

    public static Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> map() {
        Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> result = map;
        if (result == null) {
            synchronized (CraftType.class) {
                result = map;
                if (result == null) {
                    result = buildMap();
                    map = result;
                }
            }
        }
        return result;
    }
}
