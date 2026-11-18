/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Pins how the descriptions of the calendar file write the web conference
 * link and the names they attribute the meeting to: the HTML one writes them
 * escaped, and neither writes a link running a script. The link is the one
 * the event stored, which may predate the refusal of such links at save.
 */
class EventIcsBuilderConferenceLinkTest {

  private static final String LINK         = "https://meet.example.com/room?a=1&b=\"2\"<x>";

  private static final String ESCAPED_LINK = "https://meet.example.com/room?a=1&amp;b=&quot;2&quot;&lt;x&gt;";

  @Test
  void testHtmlDescriptionEscapesTheConferenceLink() {
    String html = EventIcsBuilder.htmlDescription(Locale.ENGLISH, "Ada", "Chemistry", LINK, null, null);

    assertTrue(html.contains("<a href=\"" + ESCAPED_LINK + "\">" + ESCAPED_LINK + "</a>"), html);
    assertFalse(html.contains("\"2\""), html);
  }

  @Test
  void testHtmlDescriptionLeavesOutAScriptLink() {
    for (String url : ConferenceLinkSchemeTest.SCRIPT_URLS) {
      String html = EventIcsBuilder.htmlDescription(Locale.ENGLISH, "Ada", "Chemistry", url, null, null);

      assertFalse(html.contains("<a "), url);
    }
  }

  @Test
  void testHtmlDescriptionEscapesTheNames() {
    String html = EventIcsBuilder.htmlDescription(Locale.ENGLISH, "Ada <b>\"L\"</b>", "Chem & <i>Co</i>", null, null, null);

    assertTrue(html.contains("<b>Ada &lt;b&gt;&quot;L&quot;&lt;/b&gt;</b>"), html);
    assertTrue(html.contains("<b>Chem &amp; &lt;i&gt;Co&lt;/i&gt;</b>"), html);
  }

  @Test
  void testDescriptionKeepsAConferenceLink() {
    String text = EventIcsBuilder.description(Locale.ENGLISH, "Ada", "Chemistry", LINK, null, null, null);

    assertTrue(text.contains(LINK), text);
  }

  @Test
  void testDescriptionLeavesOutAScriptLink() {
    for (String url : ConferenceLinkSchemeTest.SCRIPT_URLS) {
      String text = EventIcsBuilder.description(Locale.ENGLISH, "Ada", "Chemistry", url, null, null, null);

      assertFalse(text.contains(url), url);
    }
  }

}
