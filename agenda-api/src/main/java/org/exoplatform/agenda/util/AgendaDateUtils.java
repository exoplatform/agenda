/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public class AgendaDateUtils {
  public static final DateTimeFormatter RFC_3339_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX")
                                                                              .withResolverStyle(ResolverStyle.LENIENT);

  private AgendaDateUtils() {
  }

  public static ZonedDateTime parseRFC3339Date(String dateString) {
    return ZonedDateTime.parse(dateString, RFC_3339_FORMATTER);
  }

  public static String toRFC3339Date(ZonedDateTime dateTime) {
    return dateTime.format(RFC_3339_FORMATTER);
  }

}
