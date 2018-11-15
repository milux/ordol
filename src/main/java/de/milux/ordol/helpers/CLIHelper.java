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
package de.milux.ordol.helpers;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

public final class CLIHelper {
  public static int validateInt(CommandLine cmd, String option, int min, int max, int def)
      throws ParseException {
    Number iNum = (Number) cmd.getParsedOptionValue(option);
    if (iNum != null) {
      int i = iNum.intValue();
      if (i < min || i > max) {
        throw new ParseException(
            i
                + " is an invalid value for \"-mth\", must be in range "
                + "["
                + min
                + ";"
                + max
                + "]!");
      }
      return i;
    }
    return def;
  }

  public static double validateDouble(
      CommandLine cmd, String option, double min, double max, double def) throws ParseException {
    Number dNum = (Number) cmd.getParsedOptionValue(option);
    if (dNum != null) {
      double d = dNum.doubleValue();
      if (d < min || d > max) {
        throw new ParseException(
            d
                + " is an invalid value for \"-mth\", must be in range "
                + "["
                + min
                + ";"
                + max
                + "]!");
      }
      return d;
    }
    return def;
  }
}
