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
package org.exoplatform.agenda.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assume;
import org.junit.Test;

import org.exoplatform.groovyscript.GroovyTemplate;

/**
 * Renders the mail and push templates of the "Participation confirmation"
 * notification, so that what each recipient reads, and the links a guest
 * without an account is not given, are pinned. The templates live in the
 * <code>agenda-webapps</code> module and are read from the source tree of the
 * reactor.
 */
public class EventParticipationConfirmedTemplatesTest {

  private static final String TEMPLATES_PATH = "../agenda-webapps/src/main/webapp/WEB-INF/conf/agenda/templates/notification/";

  private static final String MAIL_TEMPLATE  = TEMPLATES_PATH + "mail/EventParticipationConfirmedMailPlugin.gtmpl";

  private static final String PUSH_TEMPLATE  = TEMPLATES_PATH + "push/EventParticipationConfirmedPushPlugin.gtmpl";

  private static final String EVENT_URL      = "http://host/portal/dw/agenda?parentId=1&occurrenceId=2026-10-22T09:00:00Z";

  private static final String FOOTER_KEY     = "[Notification.label.footer|http://host/portal/dw/settings]";

  /**
   * A user reads the confirmation, the event's title, and gets the button
   * opening the event and the footer of their settings.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testAUserMailHasTheTwoLinesAndTheButton() throws Exception {
    String mail = render(MAIL_TEMPLATE, false, "", "", 0, false);

    assertTrue(mail.contains("[Notification.agenda.event.participationConfirmed]"));
    assertTrue(mail.contains("Weekly sync"));
    assertTrue(mail.contains("href=\"" + EVENT_URL + "\""));
    assertTrue(mail.contains("[Notification.agenda.event.open]"));
    assertTrue(mail.contains("Alice"));
    assertTrue(mail.contains(FOOTER_KEY));
  }

  /**
   * A guest without an account reads the same lines, without the link to
   * the event, their first name or the footer of the settings of an account
   * they do not have.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testAGuestMailHasNoLinkReservedToUsers() throws Exception {
    String mail = render(MAIL_TEMPLATE, true, "Thursday, October 22, 2026", "", 2, false);

    assertTrue(mail.contains("[Notification.agenda.event.participationConfirmed]"));
    assertTrue(mail.contains("Weekly sync"));
    assertTrue(mail.contains("[Notification.agenda.event.participationConfirmed.otherDates|Thursday, October 22, 2026|2]"));
    assertFalse(mail.contains(EVENT_URL));
    assertFalse(mail.contains("[Notification.agenda.event.open]"));
    assertFalse(mail.contains("Alice"));
    assertFalse(mail.contains(FOOTER_KEY));
  }

  /**
   * An event that is not recurring names no date.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testNoDateIsNamedOnAnEventNotRecurring() throws Exception {
    for (String template : new String[] { MAIL_TEMPLATE, PUSH_TEMPLATE }) {
      String message = render(template, false, "", "", 0, false);

      assertFalse(template, message.contains("participationConfirmed.dateTime"));
      assertFalse(template, message.contains("participationConfirmed.other"));
      assertFalse(template, message.contains("participationConfirmed.followingDates"));
    }
  }

  /**
   * On a series, the third line gives the first date promoted, with its time
   * on a timed series, then the number of the other dates promoted, or says
   * the following dates when the seats have no last date.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testTheThirdLineCountsTheOtherDates() throws Exception {
    for (String template : new String[] { MAIL_TEMPLATE, PUSH_TEMPLATE }) {
      String timed = "[Notification.agenda.event.participationConfirmed.dateTime|Thursday, October 22, 2026|10:00]";
      assertTrue(template, render(template, false, "Thursday, October 22, 2026", "10:00", 0, false).contains(timed));
      assertTrue(template,
                 render(template, false, "Thursday, October 22, 2026", "10:00", 1, false)
                                                                                     .contains("[Notification.agenda.event.participationConfirmed.otherDate|"
                                                                                         + timed + "]"));
      assertTrue(template,
                 render(template, false, "Thursday, October 22, 2026", "", 4, false)
                                                                                .contains("[Notification.agenda.event.participationConfirmed.otherDates|Thursday, October 22, 2026|4]"));
      String openEnded = render(template, false, "Thursday, October 22, 2026", "", 0, true);
      assertTrue(template,
                 openEnded.contains("[Notification.agenda.event.participationConfirmed.followingDates|Thursday, October 22, 2026]"));
      assertFalse(template, openEnded.contains("participationConfirmed.other"));
    }
  }

  private String render(String templatePath,
                        boolean guest,
                        String promotedDay,
                        String promotedTime,
                        int otherDates,
                        boolean openEnded) throws Exception {
    GroovyTemplate template = new GroovyTemplate(templatePath, templatePath, readTemplate(templatePath));
    Map<String, Object> binding = new HashMap<>();
    binding.put("_ctx", new TemplateContextStub());
    binding.put("_templateContext", new HashMap<>());
    binding.put("isGuest", guest);
    binding.put("eventTitle", "Weekly sync");
    binding.put("eventURL", guest ? "" : EVENT_URL);
    binding.put("FIRSTNAME", "Alice");
    binding.put("FOOTER_LINK", "http://host/portal/dw/settings");
    binding.put("LAST_UPDATED_TIME", "now");
    binding.put("promotedDay", promotedDay);
    binding.put("promotedTime", promotedTime);
    binding.put("otherDates", otherDates);
    binding.put("openEnded", openEnded);
    return template.render(binding);
  }

  private String readTemplate(String templatePath) throws IOException {
    File templateFile = new File(templatePath);
    Assume.assumeTrue("The template of the webapp module is not reachable from this module", templateFile.exists());
    return new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
  }

  /**
   * Minimal stand-in for the notification template context: a label is
   * echoed as its key followed by its arguments, so that the rendering shows
   * which label is used with what.
   */
  public static class TemplateContextStub {

    /**
     * @param key key of the label to resolve
     * @param args arguments of the label
     * @return the key and the arguments, between brackets
     */
    public String appRes(String key, Object... args) {
      if (args == null || args.length == 0) {
        return "[" + key + "]";
      }
      return "[" + key + "|" + Arrays.stream(args).map(String::valueOf).collect(Collectors.joining("|")) + "]";
    }

    /**
     * @param text text to escape
     * @return the text itself, unchanged
     */
    public String escapeHTML(String text) {
      return text;
    }

    /**
     * Stands for the inclusion of the shared header and footer templates.
     *
     * @param path path of the template to include, ignored
     * @param templateContext context of the included template, ignored
     */
    public void include(String path, Object templateContext) {
      // The included templates hold nothing this test looks at
    }
  }
}
