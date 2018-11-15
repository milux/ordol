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
import static org.junit.Assert.assertEquals;

import de.milux.ordol.helpers.IOHelper;
import org.junit.Test;

public class IOHelperTest {
  @Test
  public void testFileSHA256() {
    String hash = IOHelper.getSHA256(FS.getPath("k9mail-alpha.apk"));
    assertEquals("88D46935DA026BDEDE3D2CFA7D46A7D9E0FE9382952E8E20054E1617A8EBF437", hash);
  }
}
