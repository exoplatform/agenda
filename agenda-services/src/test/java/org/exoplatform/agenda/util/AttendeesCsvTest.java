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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pins the CSV the participant list is exported as: UTF-8 byte order mark,
 * comma, CRLF, every cell quoted, no cell a spreadsheet would evaluate, and a
 * file name every file system accepts.
 */
class AttendeesCsvTest {

  /**
   * The file starts with the byte order mark, separates cells with a comma and
   * ends every line, the last one included, with CRLF.
   */
  @Test
  void theFileIsUtf8WithAByteOrderMarkCommasAndCrlf() {
    String csv = AttendeesCsv.write(List.of(List.of("Name", "Answer"), List.of("Jane Doe", "Yes")));

    assertEquals("\uFEFF\"Name\",\"Answer\"\r\n\"Jane Doe\",\"Yes\"\r\n", csv);
  }

  /**
   * A comma, a quote or a line break stays inside its cell: every cell is
   * quoted and its quotes doubled.
   */
  @Test
  void aCellKeepsItsCommasQuotesAndLineBreaks() {
    assertEquals("\"Doe, Jane\"", AttendeesCsv.cell("Doe, Jane"));
    assertEquals("\"Jane \"\"JD\"\" Doe\"", AttendeesCsv.cell("Jane \"JD\" Doe"));
    assertEquals("\"Jane\nDoe\"", AttendeesCsv.cell("Jane\nDoe"));
    assertEquals("\"\"", AttendeesCsv.cell(null));
    assertEquals("\"\"", AttendeesCsv.cell(""));
  }

  /**
   * Each character a spreadsheet reads as the start of a formula gets a single
   * quote before it, and only at the start of a cell: a name written as a
   * formula is shown, never evaluated.
   */
  @Test
  void noCellIsReadAsAFormula() {
    assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", AttendeesCsv.cell("=HYPERLINK(\"http://x\")"));
    assertEquals("\"'+1+1\"", AttendeesCsv.cell("+1+1"));
    assertEquals("\"'-1+1\"", AttendeesCsv.cell("-1+1"));
    assertEquals("\"'@SUM(A1)\"", AttendeesCsv.cell("@SUM(A1)"));
    assertEquals("\"'\t=1\"", AttendeesCsv.cell("\t=1"));
    assertEquals("\"'\r=1\"", AttendeesCsv.cell("\r=1"));
    assertEquals("\"Jane=Doe-Smith\"",
                 AttendeesCsv.cell("Jane=Doe-Smith"),
                 "a character of a formula elsewhere than first stays as it is");
  }

  /**
   * The file is named after the day and the title, the title's characters a
   * file system refuses and its control characters replaced.
   */
  @Test
  void theFileIsNamedAfterTheDayAndTheTitle() {
    assertEquals("20261020_Team meeting_participants list.csv",
                 AttendeesCsv.fileName(LocalDate.of(2026, 10, 20), "Team meeting"));
    assertEquals("20260105_Q4_ plan_budget _ _a_ _b_ _c_ _d___participants list.csv",
                 AttendeesCsv.fileName(LocalDate.of(2026, 1, 5), "Q4: plan/budget * \"a\" <b> |c| \\d\\?"));
    assertEquals("20261020_Team_meeting_participants list.csv",
                 AttendeesCsv.fileName(LocalDate.of(2026, 10, 20), " Team\u0007meeting\r\n"),
                 "the title is trimmed and its control characters replaced");
    assertEquals("20261020_Réunion d'équipe_participants list.csv",
                 AttendeesCsv.fileName(LocalDate.of(2026, 10, 20), "Réunion d'équipe"),
                 "an accented title stays as typed");
    assertEquals("20261020__participants list.csv", AttendeesCsv.fileName(LocalDate.of(2026, 10, 20), null));
  }

}
