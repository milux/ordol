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
import de.milux.ordol.data.ClassData;
import de.milux.ordol.data.MethodData;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassDataAdapter implements JsonSerializer<ClassData>, JsonDeserializer<ClassData> {
  public static final String NAME_KEY = "name";
  public static final String METHODS_KEY = "methods";
  public static final String LIB_SUPERCLASS_KEY = "super";
  public static final String APP_SUPERCLASS_KEY = "superType";
  public static final String INTERFACES_KEY = "interfaces";
  public static final Type STRING_SET_TYPE = new TypeToken<HashSet<String>>() {}.getType();
  public static final Type METHOD_LIST_TYPE = new TypeToken<ArrayList<MethodData>>() {}.getType();

  @Override
  public ClassData deserialize(JsonElement j, Type type, JsonDeserializationContext jsonCtx) {
    JsonObject o = j.getAsJsonObject();
    String name = o.get(NAME_KEY).getAsString();
    List<MethodData> methods = jsonCtx.deserialize(o.get(METHODS_KEY), METHOD_LIST_TYPE);
    String libSuperClass =
        o.has(LIB_SUPERCLASS_KEY) ? o.get(LIB_SUPERCLASS_KEY).getAsString() : null;
    String appSuperClass =
        o.has(APP_SUPERCLASS_KEY) ? o.get(APP_SUPERCLASS_KEY).getAsString() : null;
    Set<String> interfaces = jsonCtx.deserialize(o.get(INTERFACES_KEY), STRING_SET_TYPE);
    return new ClassData(name, methods, libSuperClass, appSuperClass, interfaces);
  }

  @Override
  public JsonElement serialize(ClassData cd, Type type, JsonSerializationContext jsonCtx) {
    JsonObject o = new JsonObject();
    o.addProperty(NAME_KEY, cd.name);
    o.add(METHODS_KEY, jsonCtx.serialize(cd, METHOD_LIST_TYPE));
    o.addProperty(LIB_SUPERCLASS_KEY, cd.libSuperClass);
    o.addProperty(APP_SUPERCLASS_KEY, cd.appSuperClass);
    o.add(INTERFACES_KEY, jsonCtx.serialize(cd.interfaces, STRING_SET_TYPE));
    return o;
  }
}
