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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import org.exoplatform.groovyscript.GroovyTemplate;

/**
 * Renders the mail template of the event invitation notification, so that the
 * links it offers to each population of attendees are pinned. The template lives
 * in the <code>agenda-webapps</code> module and is therefore read from the
 * source tree of the reactor rather than from the classpath.
 */
public class EventMailPluginTemplateTest {

  private static final String TEMPLATE_PATH        =
                                            "../agenda-webapps/src/main/webapp/WEB-INF/conf/agenda/templates/notification/mail/EventMailPlugin.gtmpl";

  private static final String ACCEPTED_URL         = "http://host/rest/accepted";

  private static final String DECLINED_URL         = "http://host/rest/declined";

  private static final String TENTATIVE_URL        = "http://host/rest/tentative";

  private static final String PORTAL_EVENT_URL     = "http://host/portal/dw/agenda?eventId=1";

  /**
   * An external attendee has no account, so the tokenised answer links are the
   * only way they can answer at all: they must be part of the message sent to
   * them.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testGuestAttendeeGetsTheAnswerLinks() throws Exception {
    String mailContent = renderInvitationMail(true);
    assertTrue("an external attendee must be offered the accept link", mailContent.contains(ACCEPTED_URL));
    assertTrue("an external attendee must be offered the decline link", mailContent.contains(DECLINED_URL));
    assertTrue("an external attendee must be offered the tentative link", mailContent.contains(TENTATIVE_URL));
  }

  /**
   * The deep link opens a page of the portal, behind a login form: it is
   * meaningless for an attendee who has no account and must not be sent to them.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testGuestAttendeeDoesNotGetThePortalLink() throws Exception {
    String mailContent = renderInvitationMail(true);
    assertFalse("an external attendee must not be sent a link to the portal", mailContent.contains(PORTAL_EVENT_URL));
  }

  /**
   * What an internal attendee receives is left untouched: the three answer links
   * and the deep link to the event page of the portal.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testInternalAttendeeKeepsAllLinks() throws Exception {
    String mailContent = renderInvitationMail(false);
    assertTrue("an internal attendee must be offered the accept link", mailContent.contains(ACCEPTED_URL));
    assertTrue("an internal attendee must be offered the decline link", mailContent.contains(DECLINED_URL));
    assertTrue("an internal attendee must be offered the tentative link", mailContent.contains(TENTATIVE_URL));
    assertTrue("an internal attendee must keep the link to the event page", mailContent.contains(PORTAL_EVENT_URL));
  }

  /**
   * Compiles and renders the invitation mail template for a newly created event,
   * as seen by an attendee who is not the creator of the event.
   *
   * @param guest whether the attendee receiving the message is an external
   *          attendee having no account on the platform
   * @return the HTML content of the rendered message
   * @throws Exception when the template cannot be compiled or rendered
   */
  private String renderInvitationMail(boolean guest) throws Exception {
    GroovyTemplate template = new GroovyTemplate("EventMailPlugin", "EventMailPlugin", readTemplate());
    Map<String, Object> binding = new HashMap<>();
    binding.put("_ctx", new TemplateContextStub());
    binding.put("_templateContext", new HashMap<>());
    binding.put("modificationType", "ADDED");
    binding.put("eventStatus", "CONFIRMED");
    binding.put("isGuest", guest);
    binding.put("isCreator", false);
    binding.put("eventTitle", "Event title");
    binding.put("creatorName", "Creator name");
    binding.put("modifierName", "Modifier name");
    binding.put("modifierProfileUrl", "http://host/portal/dw/profile/modifier");
    binding.put("acceptedResponseURL", ACCEPTED_URL);
    binding.put("declinedResponseURL", DECLINED_URL);
    binding.put("tentativeResponseURL", TENTATIVE_URL);
    binding.put("eventURL", PORTAL_EVENT_URL);
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
    return template.render(binding);
  }

  /**
   * Reads the mail template from the source tree of the sibling webapp module.
   * The test is skipped when that module is not reachable, which happens when
   * this module is built outside of its reactor.
   *
   * @return the raw content of the template
   * @throws IOException when the template file cannot be read
   */
  private String readTemplate() throws IOException {
    File templateFile = new File(TEMPLATE_PATH);
    Assume.assumeTrue("The mail template of the webapp module is not reachable from this module",
                      templateFile.exists());
    return new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
  }

  /**
   * Minimal stand-in for the notification template context, exposing only the
   * three methods the invitation mail template calls on it.
   */
  public static class TemplateContextStub {

    /**
     * Stands for the resolution of an internationalized label, echoing the key
     * instead of translating it so that the rendering does not depend on any
     * resource bundle.
     *
     * @param key key of the label to resolve
     * @param args arguments of the label, ignored
     * @return the key itself, surrounded by brackets
     */
    public String appRes(String key, Object... args) {
      return "[" + key + "]";
    }

    /**
     * Stands for the HTML escaping applied by the notification context.
     *
     * @param text text to escape
     * @return the text itself, unchanged
     */
    public String escapeHTML(String text) {
      return text;
    }

    /**
     * Stands for the inclusion of the shared header and footer templates, which
     * carry no invitation link and are not part of what this test pins.
     *
     * @param path path of the template to include, ignored
     * @param templateContext context of the included template, ignored
     */
    public void include(String path, Object templateContext) {
      // The included templates hold nothing this test looks at
    }
  }
}
