package org.eyecraft.client;

import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.context.ContextType;

public final class RecipeContexts {
  public static final ContextType EMPTY_TYPE =
      new ContextType.Builder().build();                    // no required/allowed params
  public static final ContextParameterMap EMPTY_CTX =
      new ContextParameterMap.Builder().build(EMPTY_TYPE);  // empty map bound to empty type
}