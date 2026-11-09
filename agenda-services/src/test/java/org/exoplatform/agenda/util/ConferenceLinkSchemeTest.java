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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import org.exoplatform.agenda.model.EventConference;

/**
 * Pins which web conference links {@link Utils#isScriptUrl(String)} recognises
 * as running a script, and which it leaves alone.
 */
public class ConferenceLinkSchemeTest {

  /**
   * The schemes a browser runs or opens in the page, written as a browser
   * still reads them: in any case, after leading spaces and control
   * characters, and with tabs or line breaks inside.
   */
  public static final List<String> SCRIPT_URLS = Arrays.asList("javascript:alert(1)",
                                                        "vbscript:msgbox(1)",
                                                        "data:text/html;base64,PHNjcmlwdD4=",
                                                        "JavaScript:alert(1)",
                                                        "java\tscript:alert(1)",
                                                        "java\nscript:alert(1)",
                                                        " \u0001javascript:alert(1)",
                                                        "DATA:text/html,x");

  /**
   * Links that are not scripts: other schemes, a relative link, a link with
   * no scheme, and links merely containing the words.
   */
  public static final List<String> OTHER_URLS  = Arrays.asList("https://meet.example.com/room?a=1&b=2",
                                                        "http://meet.example.com/room",
                                                        "/portal/dw/jitsi/room",
                                                        "meet.example.com/room",
                                                        "https://javascript.example.com/data:",
                                                        "mailto:someone@example.com",
                                                        "javascriptx:alert(1)");

  @Test
  public void testScriptUrlsAreRecognised() {
    for (String url : SCRIPT_URLS) {
      assertTrue("Not recognised as a script link: " + url, Utils.isScriptUrl(url));
    }
  }

  @Test
  public void testOtherUrlsAreNotRecognised() {
    for (String url : OTHER_URLS) {
      assertFalse("Recognised as a script link: " + url, Utils.isScriptUrl(url));
    }
    assertFalse(Utils.isScriptUrl(null));
    assertFalse(Utils.isScriptUrl(""));
  }

  /**
   * The scheme is compared without case in every locale: in Turkish, the
   * default lower case of <code>I</code> is a dotless i.
   */
  @Test
  public void testScriptUrlIsRecognisedWhateverTheDefaultLocale() {
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.of("tr"));
      assertTrue(Utils.isScriptUrl("JAVASCRIPT:alert(1)"));
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  public void testCheckConferenceUrlsRefusesAScriptLink() {
    List<EventConference> conferences = Arrays.asList(conference("https://meet.example.com/room"),
                                                      conference("JavaScript:alert(1)"));
    assertThrows(IllegalArgumentException.class, () -> Utils.checkConferenceUrls(conferences));
  }

  @Test
  public void testCheckConferenceUrlsAcceptsOtherLinks() {
    Utils.checkConferenceUrls(null);
    Utils.checkConferenceUrls(Collections.emptyList());
    Utils.checkConferenceUrls(Arrays.asList(conference("https://meet.example.com/room"), conference(null)));
  }

  private static EventConference conference(String url) {
    return new EventConference(0, 0, "webrtc", url, null, null, null);
  }

}
