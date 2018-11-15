/*
 * ordol
 * 
 * Copyright (C) 2018 Michael Lux, Fraunhofer AISEC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.milux.ordol.gson;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.milux.ordol.data.MethodData;
import de.milux.ordol.data.UnitData;
import java.lang.reflect.Type;
import java.util.*;

public class MethodDataAdapter implements JsonSerializer<MethodData>, JsonDeserializer<MethodData> {
  public static final String NAME_KEY = "name";
  public static final String FIX_TYPES_KEY = "fixTypes";
  public static final String BLOCKS_KEY = "blocks";
  public static final String BLOCKS_SUCCS_KEY = "blockSuccs";
  public static final String CTPH_KEY = "ctph";
  public static final String REFS_KEY = "refs";
  public static final String IDX_KEY = "idx";
  public static final Type STRING_LIST_TYPE = new TypeToken<ArrayList<String>>() {}.getType();
  public static final Type UNIT_LIST_LIST_TYPE = new TypeToken<List<List<UnitData>>>() {}.getType();
  public static final Type INT_INT_SET_MAP_TYPE = new TypeToken<HashMap<Integer, HashSet<Integer>>>() {}.getType();

  @Override
  public MethodData deserialize(
      JsonElement jsonElement, Type type, JsonDeserializationContext jsonCtx) {
    JsonObject o = jsonElement.getAsJsonObject();
    String name = o.get(NAME_KEY).getAsString();
    List<String> fixTypes = jsonCtx.deserialize(o.get(FIX_TYPES_KEY), STRING_LIST_TYPE);
    List<List<UnitData>> blocks = jsonCtx.deserialize(o.get(BLOCKS_KEY), UNIT_LIST_LIST_TYPE);
    Map<Integer, Set<Integer>> blockSuccs =
        jsonCtx.deserialize(o.get(BLOCKS_SUCCS_KEY), INT_INT_SET_MAP_TYPE);
    String ctph = o.has(CTPH_KEY) ? o.get(CTPH_KEY).getAsString() : null;
    List<String> refs = jsonCtx.deserialize(o.get(REFS_KEY), STRING_LIST_TYPE);
    int idxInClass = o.get(IDX_KEY).getAsInt();
    return new MethodData(name, fixTypes, blocks, blockSuccs, ctph, refs, idxInClass);
  }

  @Override
  public JsonElement serialize(MethodData md, Type type, JsonSerializationContext jsonCtx) {
    JsonObject o = new JsonObject();
    o.addProperty(NAME_KEY, md.name);
    o.add(FIX_TYPES_KEY, jsonCtx.serialize(md.fixTypes));
    o.add(BLOCKS_KEY, jsonCtx.serialize(md.blocks));
    o.add(BLOCKS_SUCCS_KEY, jsonCtx.serialize(md.blockSuccs));
    o.addProperty(CTPH_KEY, md.ctph);
    o.add(REFS_KEY, jsonCtx.serialize(md.refs));
    o.addProperty(IDX_KEY, md.idxInClass);
    return o;
  }
}
