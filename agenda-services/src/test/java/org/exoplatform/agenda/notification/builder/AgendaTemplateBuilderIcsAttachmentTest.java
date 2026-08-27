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
package org.exoplatform.agenda.notification.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.junit.Assert;
import org.junit.Test;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.model.AgendaConnectorAccount;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.AgendaNotificationPlugin;
import org.exoplatform.agenda.notification.provider.MailTemplateProvider;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.ArgumentLiteral;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

/**
 * The truth table of the ICS suppression is pinned on its own predicate by
 * {@code IcsAttachmentSuppressionTest}. This one pins the <b>wiring</b>: that
 * the message the mail channel actually builds carries — or does not carry —
 * the {@code event.ics} part accordingly.
 *
 * <p>
 * It exists because the predicate being right proves nothing about the mail if
 * nothing calls it. It runs the real builder, over a real event created in the
 * container, against the recipient's real persisted settings.
 */
public class AgendaTemplateBuilderIcsAttachmentTest extends BaseAgendaEventTest {

  private static final ArgumentLiteral<String> EVENT_TITLE = new ArgumentLiteral<>(String.class, "eventTitle");

  private static final String                  CALDAV        = "agenda.caldavCalendar";

  private static final String                  GUEST_ADDRESS = "guest@example.com";

  /**
   * A recipient connected to CalDAV with meeting copies on receives the mail
   * without the file: their copy is coming, and the file would duplicate it.
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testNoIcsForAConnectedUserWithCopiesEnabled() throws Exception {
    connectCaldav(testuser2Identity.getId(), true, true);

    MessageInfo messageInfo = buildMailFor("testuser2");

    Assert.assertNotNull("the message must still be built, only its attachment goes", messageInfo);
    Assert.assertNull("a user who will hold a synced copy must receive no event.ics",
                      messageInfo.getAttachment());
  }

  /**
   * The same recipient with copies switched off keeps the file: nothing will
   * be pushed to them, so the file is the only calendar object they get.
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testIcsKeptWhenCopiesAreDisabled() throws Exception {
    connectCaldav(testuser2Identity.getId(), true, false);

    MessageInfo messageInfo = buildMailFor("testuser2");

    Assert.assertNotNull(messageInfo);
    assertHasIcs(messageInfo);
  }

  /**
   * A recipient with no connected account keeps the file — the common case,
   * and the one that must not regress.
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testIcsKeptWhenNotConnected() throws Exception {
    MessageInfo messageInfo = buildMailFor("testuser3");

    Assert.assertNotNull(messageInfo);
    assertHasIcs(messageInfo);
  }

  /**
   * A guest keeps the file under any condition. A guest is not an organization
   * identity, so the builder resolves no identity id for it and reads the
   * default settings; the file must survive both of those.
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testIcsAlwaysKeptForAGuest() throws Exception {
    // The invariant the guest clause rests on: a guest's address is not an
    // organization identity, so the builder resolves no id for it. If this
    // ever stopped holding, the clause below would be resting on nothing.
    Assert.assertEquals("a guest must not resolve to an organization identity",
                        0,
                        Utils.getIdentityIdByUsername(identityManager, GUEST_ADDRESS));

    MessageInfo messageInfo = buildMailFor(GUEST_ADDRESS);

    Assert.assertNotNull(messageInfo);
    assertHasIcs(messageInfo);
  }

  /**
   * Only the attachment goes. The Accept / Decline / Tentative links are built
   * elsewhere — {@code NotificationUtils.buildTemplateParameters}, called
   * before the attachment block and untouched by it — and a recipient who
   * loses the file must still be able to answer from the mail.
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testRsvpLinksSurviveTheSuppression() throws Exception {
    connectCaldav(testuser2Identity.getId(), true, true);

    NotificationInfo notification = createdEventNotification();
    notification.setTo("testuser2");
    TemplateContext templateContext = NotificationUtils.buildTemplateParameters("testuser2",
                                                                               spaceService,
                                                                               agendaEventAttendeeService,
                                                                               new MailTemplateProvider(container,
                                                                                                        channelInitParams()),
                                                                               notification,
                                                                               ZoneOffset.UTC);

    assertResponseLink(templateContext, "acceptedResponseURL");
    assertResponseLink(templateContext, "declinedResponseURL");
    assertResponseLink(templateContext, "tentativeResponseURL");
  }

  /**
   * Asserts one RSVP link is present in the rendered parameters as a real URL.
   *
   * @param templateContext parameters the mail template is rendered with
   * @param variableName name of the template variable holding the link
   */
  private void assertResponseLink(TemplateContext templateContext, String variableName) {
    Object url = templateContext.get(variableName);
    Assert.assertTrue(variableName + " must be a non-blank URL, was: " + url,
                      url instanceof String link && StringUtils.startsWith(link, "http"));
  }

  /**
   * Builds the mail message the agenda would send to one recipient for a newly
   * created space event.
   *
   * @param recipient username the mail is built for, or a guest's address
   * @return the message the mail channel would send
   * @throws Exception when the event or the notification cannot be built
   */
  private MessageInfo buildMailFor(String recipient) throws Exception {
    NotificationInfo notification = createdEventNotification();
    notification.setTo(recipient);

    NotificationContext ctx = NotificationContextImpl.cloneInstance().setNotificationInfo(notification);
    MailTemplateProvider templateProvider = new MailTemplateProvider(container, channelInitParams());
    AgendaTemplateBuilder builder = new AgendaTemplateBuilder(templateProvider,
                                                             container,
                                                             NotificationUtils.EVENT_ADDED_KEY,
                                                             false,
                                                             false);
    MessageInfo messageInfo = builder.makeMessage(ctx);
    // The builder swallows every failure and answers null. Left as it is, a
    // rendering failure would read as "no attachment" and pass the very
    // assertion these tests exist to make: re-thrown here so the suite says
    // what actually went wrong.
    if (messageInfo == null && ctx.getException() != null) {
      throw new IllegalStateException("The notification could not be built", ctx.getException());
    }
    return messageInfo;
  }

  /**
   * The notification the platform raises when a space event is created, built
   * by the real plugin over a real event so that every stored parameter the
   * builder reads is present.
   *
   * @return the notification, addressed to nobody in particular yet
   * @throws Exception when the event cannot be created
   */
  private NotificationInfo createdEventNotification() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start, false);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser1Identity,
                                     testuser2Identity,
                                     testuser3Identity,
                                     spaceIdentity);

    // The registered plugin id, not an invented one: the mail rendering reads
    // the PluginConfig the test container registers under this very name, and
    // fails outright on anything else.
    InitParams initParams = new InitParams();
    ValueParam pluginKey = new ValueParam();
    pluginKey.setName("agenda.notification.plugin.key");
    pluginKey.setValue(NotificationUtils.AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);
    initParams.addParam(pluginKey);

    AgendaNotificationPlugin plugin = new AgendaNotificationPlugin(initParams,
                                                                  identityManager,
                                                                  agendaCalendarService,
                                                                  spaceService);
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, createdEvent)
                                                     .append(NotificationUtils.EVENT_ATTENDEE,
                                                             agendaEventAttendeeService.getEventAttendees(createdEvent.getId())
                                                                                       .getEventAttendees())
                                                     .append(EVENT_TITLE, createdEvent.getSummary())
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             AgendaEventModificationType.ADDED.name());
    return plugin.makeNotification(ctx);
  }

  /**
   * The init params a notification template provider is registered with.
   *
   * @return init params naming the mail channel
   */
  private InitParams channelInitParams() {
    InitParams initParams = new InitParams();
    ValueParam channelId = new ValueParam();
    channelId.setName("channel-id");
    channelId.setValue("MAIL_CHANNEL");
    initParams.addParam(channelId);
    return initParams;
  }

  /**
   * An internal recipient's mailed document says where the meeting lives: URL
   * names the event in eXo, and the description repeats it on a labelled line
   * because many clients never surface URL (EXO-89751).
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testMailedIcsCarriesTheEventLinkForAnInternalUser() throws Exception {
    String ics = icsOf(buildMailFor("testuser3"));

    String url = icsProperty(ics, "URL");
    Assert.assertNotNull("the mailed document must say where the event lives", url);
    Assert.assertTrue("URL must name the event in eXo: " + url, url.contains("/agenda?eventId="));

    String description = icsProperty(ics, "DESCRIPTION");
    Assert.assertNotNull(description);
    Assert.assertTrue("the description must carry the link too: " + description,
                      description.contains("/agenda?eventId="));
  }

  /**
   * A guest has no eXo account, so the link would put them on a login screen.
   * The mail is the one channel that knows who it is going to, and it leaves
   * the link out for a guest — from URL and from the description alike.
   *
   * <p>
   * The guest still gets the file itself, and everything else in it: this is
   * about one link, not about withholding the meeting.
   *
   * @throws Exception when the notification cannot be built
   */
  @Test
  public void testMailedIcsWithholdsTheEventLinkFromAGuest() throws Exception {
    MessageInfo messageInfo = buildMailFor(GUEST_ADDRESS);
    assertHasIcs(messageInfo);
    String ics = icsOf(messageInfo);

    Assert.assertNull("a guest's document must carry no URL: " + icsProperty(ics, "URL"), icsProperty(ics, "URL"));

    String description = icsProperty(ics, "DESCRIPTION");
    Assert.assertNotNull("the guest still gets a described meeting", description);
    Assert.assertFalse("and no link to a screen they cannot reach: " + description,
                       description.contains("/agenda?eventId="));
  }

  /**
   * Reads the {@code event.ics} part of a message as text, already unfolded so
   * a property broken over several lines (RFC 5545 &sect;3.1) can be matched
   * whole.
   *
   * @param messageInfo message the mail channel would send
   * @return the unfolded document
   * @throws IOException when the attachment cannot be read
   */
  private String icsOf(MessageInfo messageInfo) throws IOException {
    assertHasIcs(messageInfo);
    String text = new String(messageInfo.getAttachment().get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return text.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "");
  }

  /**
   * Reads one property out of an unfolded iCalendar document.
   *
   * @param ics unfolded document
   * @param propertyName property name, without its parameters
   * @return the whole property line, or null when the document has none
   */
  private String icsProperty(String ics, String propertyName) {
    return Arrays.stream(ics.split("\\R"))
                 .filter(line -> line.equals(propertyName) || line.startsWith(propertyName + ":")
                     || line.startsWith(propertyName + ";"))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Persists a connected CalDAV account for a user, with the two switches the
   * copy actually depends on.
   *
   * @param userIdentityId identity id of the user, as a string
   * @param automaticPushEvents value of the global copy switch
   * @param pushEnabled whether that account receives copies
   */
  private void connectCaldav(String userIdentityId, boolean automaticPushEvents, boolean pushEnabled) {
    long identityId = Long.parseLong(userIdentityId);
    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(identityId);
    settings.setAutomaticPushEvents(automaticPushEvents);
    List<AgendaConnectorAccount> accounts = new ArrayList<>();
    accounts.add(new AgendaConnectorAccount(CALDAV, "user@example.com", pushEnabled));
    settings.setConnectedConnectors(accounts);
    agendaUserSettingsService.saveAgendaUserSettings(identityId, settings);
  }

  /**
   * Asserts the message carries exactly one {@code event.ics} calendar part.
   *
   * @param messageInfo message the mail channel would send
   */
  private void assertHasIcs(MessageInfo messageInfo) {
    Assert.assertNotNull("the event.ics attachment is missing", messageInfo.getAttachment());
    Assert.assertEquals(1, messageInfo.getAttachment().size());
    Assert.assertEquals("event.ics", messageInfo.getAttachment().get(0).getName());
  }

}
