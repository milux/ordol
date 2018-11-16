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
import de.milux.ordol.data.UnitData;
import io.vavr.Tuple;

import java.lang.reflect.Type;
import java.util.Arrays;

public class UnitDataAdapter implements JsonSerializer<UnitData>, JsonDeserializer<UnitData> {
  @Override
  public UnitData deserialize(
      JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
    if (jsonElement.isJsonPrimitive()) {
      return new UnitData(jsonElement.getAsString());
    } else if (jsonElement.isJsonArray()) {
      JsonArray a = jsonElement.getAsJsonArray();
      String instr = a.get(0).getAsString();
      String[] refTypes = jsonDeserializationContext.deserialize(a.get(1), String[].class);
      if (a.size() <= 3) {
        return new UnitData(instr, refTypes, a.size() == 3);
      } else if (a.size() == 4) {
        return new UnitData(instr, refTypes, Tuple.of(a.get(2).getAsString(), a.get(3).getAsInt()));
      } else if (a.size() > 4) {
        throw new JsonParseException("Could not parse UnitData");
      }
    }
    return null;
  }

  @Override
  public JsonElement serialize(
      UnitData u, Type type, JsonSerializationContext jsonSerializationContext) {
    if (u.refTypes.length == 0) {
      return new JsonPrimitive(u.instr);
    } else {
      JsonArray a = new JsonArray();
      JsonArray refTypes = new JsonArray();
      Arrays.stream(u.refTypes).forEach(refTypes::add);
      a.add(u.instr);
      a.add(refTypes);
      if (u.hasInvocation) {
        if (u.refMethod == null) {
          a.add(JsonNull.INSTANCE);
        } else {
          a.add(u.refMethod._1);
          a.add(u.refMethod._2);
        }
      }
      return a;
    }
  }
}
