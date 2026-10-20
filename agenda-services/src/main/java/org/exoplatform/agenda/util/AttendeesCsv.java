/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes the participant list of an event as a CSV file (RFC 4180): UTF-8
 * with a byte order mark, so that a spreadsheet reads its accents, a comma
 * between cells and CRLF after each line, every cell quoted.
 * <p>
 * Names, space names and guest addresses are typed by users: a cell starting
 * with a character a spreadsheet reads as the start of a formula gets a single
 * quote before it, so that no cell of the file is ever evaluated.
 */
public final class AttendeesCsv {

  /** Starts the file, so that a spreadsheet reads it as UTF-8. */
  public static final String  BYTE_ORDER_MARK  = "﻿";

  /** Ends each line of the file. */
  public static final String  LINE_SEPARATOR   = "\r\n";

  /** Ends the name of the file, after the event's title. */
  public static final String  FILE_NAME_SUFFIX = "_participants list.csv";

  /** The characters a spreadsheet reads as the start of a formula. */
  private static final String FORMULA_STARTS   = "=+-@\t\r";

  /** The characters a file name may not hold on the usual file systems. */
  private static final String FILE_NAME_UNSAFE = "/\\:*?\"<>|";

  private AttendeesCsv() {
  }

  /**
   * @param lines the lines of the file, the header first, each a list of cells
   * @return the file's text, its byte order mark included
   */
  public static String write(List<List<String>> lines) {
    StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK);
    for (List<String> line : lines) {
      csv.append(line.stream().map(AttendeesCsv::cell).collect(Collectors.joining(",")));
      csv.append(LINE_SEPARATOR);
    }
    return csv.toString();
  }

  /**
   * @param value the text of a cell, null for an empty one
   * @return the cell as written in the file: neutralised when it would start a
   *         formula, then quoted, its quotes doubled
   */
  public static String cell(String value) {
    String text = value == null ? "" : value;
    if (!text.isEmpty() && FORMULA_STARTS.indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  /**
   * @param exportDay the day of the export
   * @param title the event's title
   * @return {@code YYYYMMDD_<title>_participants list.csv}, the title's
   *         characters a file name may not hold, and its control characters,
   *         replaced with an underscore
   */
  public static String fileName(LocalDate exportDay, String title) {
    return exportDay.format(DateTimeFormatter.BASIC_ISO_DATE) + "_" + cleanTitle(title) + FILE_NAME_SUFFIX;
  }

  private static String cleanTitle(String title) {
    if (title == null) {
      return "";
    }
    StringBuilder cleaned = new StringBuilder(title.length());
    title.trim()
         .codePoints()
         .forEach(c -> cleaned.appendCodePoint(Character.isISOControl(c) || FILE_NAME_UNSAFE.indexOf(c) >= 0 ? '_' : c));
    return cleaned.toString();
  }

}
