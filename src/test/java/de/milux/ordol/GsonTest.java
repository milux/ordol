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
package de.milux.ordol;

import static de.milux.ordol.Constants.FS;
import static de.milux.ordol.Constants.LIBS_DIRECTORY;
import static de.milux.ordol.helpers.Utils.println;
import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import de.milux.ordol.data.ClassData;
import de.milux.ordol.helpers.IOHelper;
import de.milux.ordol.helpers.Utils;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import soot.PackManager;
import soot.Scene;

public class GsonTest {

  private List<ClassData> gson27 = null;

  @Before
  public void setUp() {
    // search for a directory with libraries and call configuration functions
    LibraryMapper.configure(LIBS_DIRECTORY.resolve(FS.getPath("gson", "2.7.apk")));

    // prepare necessary classes
    println("Loading classes...");
    Scene.v().loadNecessaryClasses();

    // do transformations
    println("Running packs...");
    PackManager.v().runPacks();

    // examine classes and create library map
    println("Creating data structures...");
    gson27 =
        Scene.v().getApplicationClasses().stream().map(ClassData::new).collect(Collectors.toList());
  }

  @Test
  public void testGsonParsing() {
    Gson g = Utils.getGson();
    println("Serialize gson 2.7...");
    String json = g.toJson(gson27);
    println("Deserialize gson 2.7...");
    List<ClassData> parsedList = g.fromJson(json, IOHelper.CLASSDATA_LIST_TYPE);
    println("Compare...");
    assertEquals(gson27, parsedList);
  }

  @After
  public void tearDown() {
    soot.G.reset();
    gson27 = null;
  }
}
