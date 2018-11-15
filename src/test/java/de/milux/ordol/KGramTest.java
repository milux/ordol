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

import static org.junit.Assert.assertEquals;

import de.milux.ordol.data.KGram;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KGramTest {

  private List<List<String>> dummyBlocks = null;
  private Map<Integer, Set<Integer>> dummyBlockSuccessors = null;

  @Before
  public void setUp() {
    /**
     * This demo represents the following function: A block 0 B if (...) { C block 1 D if (...) { E
     * block 2 F G } H block 3 } else { while (...) { C block 4 } D block 5 E } R block 6
     */
    this.dummyBlocks =
        Arrays.asList(
            Arrays.asList("A", "B"), // 0
            Arrays.asList("C", "D"), // 1
            Arrays.asList("E", "F", "G"), // 2
            Collections.singletonList("H"), // 3
            Collections.singletonList("C"), // 4
            Arrays.asList("D", "E"), // 5
            Collections.singletonList("R") // 6
            );
    this.dummyBlockSuccessors = new HashMap<>();
    this.dummyBlockSuccessors.put(0, new HashSet<>(Arrays.asList(1, 4, 5)));
    this.dummyBlockSuccessors.put(1, new HashSet<>(Arrays.asList(2, 3)));
    this.dummyBlockSuccessors.put(2, new HashSet<>(Collections.singletonList(3)));
    this.dummyBlockSuccessors.put(3, new HashSet<>(Collections.singletonList(6)));
    this.dummyBlockSuccessors.put(4, new HashSet<>(Arrays.asList(4, 5)));
    this.dummyBlockSuccessors.put(5, new HashSet<>(Collections.singletonList(6)));
  }

  @Test
  public void testKGramCreation() {
    List<String> kGrams = new ArrayList<>();
    KGram.getKGrams(
        this.dummyBlocks,
        this.dummyBlockSuccessors,
        kGram -> kGrams.add(String.join("", new KGram<>(kGram))),
        String.class,
        5);
    List<String> expected =
        Arrays.asList(
            "ABCDE",
            "ABCDH",
            "ABCCC",
            "ABCCD",
            "ABCDE",
            "ABDER",
            "BCDEF",
            "BCDHR",
            "BCCCC",
            "BCCCD",
            "BCCDE",
            "BCDER", /*"BDER",*/
            "CDEFG", /*"CDHR",*/
            "DEFGH", /*"DHR",*/
            "EFGHR",
            /*"FGHR",
            "GHR",
            "HR",*/
            "CCCCC",
            "CCCCD",
            "CCCDE",
            "CCDER" /*, "CDER",*/
            /*"DER",
            "ER",
            "R"*/
            );
    assertEquals(expected, kGrams);
  }

  @After
  public void tearDown() {
    this.dummyBlocks = null;
    this.dummyBlockSuccessors = null;
  }
}
