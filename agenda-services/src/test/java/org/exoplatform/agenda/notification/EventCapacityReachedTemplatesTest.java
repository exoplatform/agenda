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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import org.exoplatform.agenda.notification.EventParticipationConfirmedTemplatesTest.TemplateContextStub;
import org.exoplatform.groovyscript.GroovyTemplate;

/**
 * Renders the mail and push templates of the "Event capacity reached"
 * notification, so that what its recipients read and the button they are
 * given are pinned. The templates live in the <code>agenda-webapps</code>
 * module and are read from the source tree of the reactor.
 */
public class EventCapacityReachedTemplatesTest {

  private static final String TEMPLATES_PATH = "../agenda-webapps/src/main/webapp/WEB-INF/conf/agenda/templates/notification/";

  private static final String MAIL_TEMPLATE  = TEMPLATES_PATH + "mail/EventCapacityReachedMailPlugin.gtmpl";

  private static final String PUSH_TEMPLATE  = TEMPLATES_PATH + "push/EventCapacityReachedPushPlugin.gtmpl";

  private static final String EVENT_URL      = "http://host/portal/dw/agenda?parentId=1&occurrenceId=2026-10-22T09:00:00Z";

  private static final String FOOTER_KEY     = "[Notification.label.footer|http://host/portal/dw/settings]";

  /**
   * The mail reads that the event filled up, names it, and gives the button
   * checking the participants, which opens the event, and the footer of the
   * recipient's settings.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testTheMailHasTheTwoLinesAndTheButton() throws Exception {
    String mail = render(MAIL_TEMPLATE);

    assertTrue(mail.contains("[Notification.agenda.event.capacityReached]"));
    assertTrue(mail.contains("Weekly sync"));
    assertTrue(mail.contains("href=\"" + EVENT_URL + "\""));
    assertTrue(mail.contains("[Notification.agenda.event.checkParticipants]"));
    assertTrue(mail.contains("Alice"));
    assertTrue(mail.contains(FOOTER_KEY));
  }

  /**
   * The push reads that the event filled up, names it, and opens the event.
   *
   * @throws Exception when the template cannot be compiled or rendered
   */
  @Test
  public void testThePushHasTheTwoLinesAndOpensTheEvent() throws Exception {
    String push = render(PUSH_TEMPLATE);

    assertTrue(push.contains("[Notification.agenda.event.capacityReached]"));
    assertTrue(push.contains("Weekly sync"));
    assertTrue(push.contains("data-link=\"" + EVENT_URL + "\""));
  }

  private String render(String templatePath) throws Exception {
    GroovyTemplate template = new GroovyTemplate(templatePath, templatePath, readTemplate(templatePath));
    Map<String, Object> binding = new HashMap<>();
    binding.put("_ctx", new TemplateContextStub());
    binding.put("_templateContext", new HashMap<>());
    binding.put("eventTitle", "Weekly sync");
    binding.put("eventURL", EVENT_URL);
    binding.put("FIRSTNAME", "Alice");
    binding.put("FOOTER_LINK", "http://host/portal/dw/settings");
    binding.put("LAST_UPDATED_TIME", "now");
    return template.render(binding);
  }

  private String readTemplate(String templatePath) throws IOException {
    File templateFile = new File(templatePath);
    Assume.assumeTrue("The template of the webapp module is not reachable from this module", templateFile.exists());
    return new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
  }
}
