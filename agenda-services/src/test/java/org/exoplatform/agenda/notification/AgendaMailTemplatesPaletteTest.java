/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.agenda.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;

import org.exoplatform.agenda.notification.EventMailPluginTemplateTest.TemplateContextStub;
import org.exoplatform.commons.notification.mail.MailPalette;
import org.exoplatform.groovyscript.GroovyTemplate;

import groovy.text.GStringTemplateEngine;

/**
 * The agenda mail templates are written on the recipient's palette: they
 * compile with the engine of the mail channel, keep no palette colour as a
 * literal, and a bound palette reaches the rendered mail. The templates live
 * in the <code>agenda-webapps</code> module and are read from the source tree
 * of the reactor.
 */
public class AgendaMailTemplatesPaletteTest {

  private static final Path    TEMPLATES       =
                                         Paths.get("../agenda-webapps/src/main/webapp/WEB-INF/conf/agenda/templates/notification/mail");

  // the light literals the palette replaces; white on a coloured button stays as written
  private static final Pattern PALETTE_LITERAL = Pattern.compile("(?i)(bgcolor=\"|background-color:\\s*)#(ffffff|f9f9f9|f1f1f1)"
      + "|(?<![a-z-])color:\\s*#(333333|999999)|(?<!replace\\(\"color: )#2f5e92|border[a-z-]*:\\s*\\d+px solid\\s*#(d8d8d8|224886)");

  @Test
  public void everyMailTemplateCompilesAndReadsThePalette() throws Exception {
    List<Path> templates = templates();
    assertEquals(9, templates.size());
    for (Path template : templates) {
      String text = Files.readString(template, StandardCharsets.UTF_8);
      new GStringTemplateEngine().createTemplate(text);
      assertFalse(template + " keeps a palette colour as a literal", PALETTE_LITERAL.matcher(text).find());
      assertTrue(template + " reads the palette", text.contains("MailPalette.of(_templateContext)"));
    }
  }

  @Test
  public void theBoundPaletteReachesTheRenderedMail() throws Exception {
    Map<String, String> palette = new HashMap<>();
    palette.put("surface", "#1E2328");
    palette.put("background", "#14181B");
    palette.put("text", "#E6EAEE");
    palette.put("secondaryText", "#9AA4AD");
    palette.put("link", "#5FA9AC");
    palette.put("border", "#3A4148");
    palette.put("primary", "#3F8487");
    palette.put(MailPalette.MODE, "dark");
    String html = render("EventMailPlugin", palette);
    assertTrue(html, html.contains("bgcolor=\"#1E2328\""));
    assertTrue(html, html.contains("color:#E6EAEE"));
    assertTrue(html, html.contains("class=\"mail-surface mail-text\""));
    assertFalse(html, html.contains("bgcolor=\"#ffffff\""));
  }

  @Test
  public void aContextWithoutPaletteRendersInTheLightColours() throws Exception {
    String html = render("EventMailPlugin", null);
    assertTrue(html, html.contains("bgcolor=\"#FFFFFF\"") || html.contains("bgcolor=\"#ffffff\""));
    assertFalse(html, html.contains("null"));
  }

  private String render(String name, Map<String, String> palette) throws Exception {
    Path file = TEMPLATES.resolve(name + ".gtmpl");
    Assume.assumeTrue("The mail templates of the webapp module are not reachable from this module", Files.exists(file));
    Map<String, Object> templateContext = new HashMap<>();
    if (palette != null) {
      templateContext.put(MailPalette.CONTEXT_KEY, palette);
    }
    Map<String, Object> binding = new HashMap<>();
    binding.put("_ctx", new TemplateContextStub());
    binding.put("_templateContext", templateContext);
    binding.put("modificationType", "ADDED");
    binding.put("eventStatus", "CONFIRMED");
    binding.put("isGuest", false);
    binding.put("isCreator", false);
    binding.put("eventTitle", "Event title");
    binding.put("creatorName", "Creator name");
    binding.put("modifierName", "Modifier name");
    binding.put("modifierProfileUrl", "http://host/portal/dw/profile/modifier");
    binding.put("acceptedResponseURL", "http://host/rest/accepted");
    binding.put("declinedResponseURL", "http://host/rest/declined");
    binding.put("tentativeResponseURL", "http://host/rest/tentative");
    binding.put("eventURL", "http://host/portal/dw/agenda?eventId=1");
    binding.put("FIRSTNAME", "Alice");
    binding.put("FOOTER_LINK", "http://host/portal/dw/settings");
    binding.put("agendaName", "Space name");
    binding.put("monthYearDate", "August 2026");
    binding.put("startDate", "10:00");
    binding.put("endDate", "11:00");
    binding.put("timeZoneName", "UTC");
    binding.put("recurrenceDetails", "");
    binding.put("eventLocation", "Meeting room");
    binding.put("eventDescription", "Event description");
    binding.put("attendees", "Alice");
    binding.put("conference", null);
    String html = new GroovyTemplate(name, name, Files.readString(file, StandardCharsets.UTF_8)).render(binding);
    assertNotNull(html);
    return html;
  }


  private List<Path> templates() throws IOException {
    Assume.assumeTrue("The mail templates of the webapp module are not reachable from this module", Files.exists(TEMPLATES));
    try (Stream<Path> files = Files.list(TEMPLATES)) {
      return files.filter(path -> path.toString().endsWith(".gtmpl")).toList();
    }
  }

}
