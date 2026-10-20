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
package org.exoplatform.agenda.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.text.Collator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jakarta.persistence.EntityManager;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeesExport;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.util.AttendeesCsv;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;

/**
 * Pins the export of a participant list on the container: the rows of the
 * date the event page serves, in the drawer's order, with the email column
 * only while the platform displays the property and Hidden for a user who hid
 * theirs; the right read on the date served; the refusals of a date poll, an
 * unknown event and a cancelled date; and the file dated in the user's zone.
 * <p>
 * The container carries no Agenda bundle: each label reads as its key, which
 * names the label chosen. The series start in sixty days, so that no date of
 * them is materialised by the reminder computing listener when they are
 * created.
 */
public class AgendaEventAttendeeExportTest extends BaseAgendaEventTest {

  private static final LocalDate    EXPORT_DAY  = LocalDate.of(2026, 10, 20);

  private static final String       FILE_NAME   = "20261020_eventSummary_participants list.csv";

  private static final List<String> HEADER      = List.of("agenda.export.header.name",
                                                          "agenda.export.header.email",
                                                          "agenda.export.header.answer");

  private static final String       YES         = "agenda.export.answer.yes";

  private static final String       NO          = "agenda.export.answer.no";

  private static final String       MAYBE       = "agenda.export.answer.maybe";

  private static final String       WAITING     = "agenda.export.answer.waitingList";

  private static final String       NO_ANSWER   = "agenda.export.answer.noAnswer";

  private static final String       GUEST_EMAIL = "guest.export@example.com";

  private final List<Long>          hiddenFor   = new ArrayList<>();

  private ProfilePropertyService    profilePropertyService;

  private AgendaEventAttendeeStorage attendeeStorage;

  private EntityManagerService      entityManagerService;

  private long                      emailSettingId;

  private boolean                   emailSettingCreated;

  private boolean                   storedActive;

  private boolean                   storedVisible;

  /**
   * Displays the email profile property, creating its setting when the
   * container has none.
   *
   * @throws Exception when the setting cannot be created
   */
  @Before
  public void displayTheEmail() throws Exception {
    profilePropertyService = container.getComponentInstanceOfType(ProfilePropertyService.class);
    attendeeStorage = container.getComponentInstanceOfType(AgendaEventAttendeeStorage.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    ProfilePropertySetting setting = profilePropertyService.getProfileSettingByName(Profile.EMAIL);
    if (setting == null) {
      setting = new ProfilePropertySetting();
      setting.setPropertyName(Profile.EMAIL);
      setting.setPropertyType("text");
      setting.setEditable(true);
      setting.setHiddenbale(true);
      setting.setActive(true);
      setting.setVisible(true);
      setting = profilePropertyService.createPropertySetting(setting);
      emailSettingCreated = true;
    }
    emailSettingId = setting.getId();
    storedActive = setting.isActive();
    storedVisible = setting.isVisible();
    displayEmail(true, true);
  }

  /**
   * Leaves the email property as it was found.
   */
  @After
  public void restoreTheEmail() {
    hiddenFor.forEach(identityId -> profilePropertyService.showPropertySetting(identityId, emailSettingId));
    hiddenFor.clear();
    if (emailSettingCreated) {
      profilePropertyService.deleteProfilePropertySetting(emailSettingId);
      emailSettingCreated = false;
    } else {
      displayEmail(storedActive, storedVisible);
    }
  }

  /**
   * The file lists the participants as the drawer shows them: the creator
   * first, then by name, the waiting list last in the order its people asked.
   * Every test user has the same name: the order by name is the guest's
   * address and the space's name against theirs, and the waiting list's order
   * is the reverse of the order its rows are stored in. A space invited as a
   * whole is named after the space, has no email and answers No answer; a
   * guest is named after their address.
   *
   * @throws Exception when the event cannot be created or answered
   */
  @Test
  public void theFileListsTheParticipantsAsTheDrawerShowsThem() throws Exception {
    Identity guest = identityManager.getOrCreateIdentity(AgendaGuestUserIdentityProvider.NAME, GUEST_EMAIL);
    Event event = openLimitedEvent(2,
                                   testuser1Identity,
                                   testuser2Identity,
                                   testuser3Identity,
                                   testuser4Identity,
                                   testuser5Identity,
                                   guest,
                                   spaceIdentity);
    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(event.getId(), testuser4Identity, EventAttendeeResponse.TENTATIVE);
    answer(event.getId(), guest, EventAttendeeResponse.DECLINED);
    answer(event.getId(), testuser5Identity, EventAttendeeResponse.ACCEPTED);
    answer(event.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    // The fifth user asked first, the third a minute later
    ZonedDateTime asked = ZonedDateTime.now(ZoneOffset.UTC).withNano(0);
    setResponseDate(event.getId(), testuser5Identity, asked);
    setResponseDate(event.getId(), testuser3Identity, asked.plusMinutes(1));
    assertEquals("the event is full: the third user waits",
                 EventAttendeeResponse.WAITING,
                 attendeeStorage.getEventAttendees(event.getId(), id(testuser3Identity)).getEventAttendees().get(0).getResponse());

    EventAttendeesExport export = export(event.getId(), null, testuser1Identity);

    assertEquals(FILE_NAME, export.fileName());
    List<List<String>> lines = lines(export);
    List<List<String>> byName = new ArrayList<>(List.of(row(testuser2Identity, YES),
                                                        row(testuser4Identity, MAYBE),
                                                        List.of(GUEST_EMAIL, GUEST_EMAIL, NO),
                                                        List.of(space.getDisplayName(), "", NO_ANSWER)));
    byName.sort(Comparator.comparing(line -> line.get(0), Collator.getInstance(Locale.ENGLISH)));
    List<List<String>> expected = new ArrayList<>();
    expected.add(HEADER);
    expected.add(row(testuser1Identity, YES));
    expected.addAll(byName);
    expected.add(row(testuser5Identity, WAITING));
    expected.add(row(testuser3Identity, WAITING));
    assertEquals(expected, lines);
  }

  /**
   * The email column exists only while the platform displays the property:
   * neither an inactive nor an invisible property gets one, and the rest of
   * the file stays.
   *
   * @throws Exception when the event cannot be created or answered
   */
  @Test
  public void theEmailColumnExistsOnlyWhileThePlatformDisplaysTheProperty() throws Exception {
    Event event = spaceEvent(false, testuser2Identity);
    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<List<String>> withoutEmail = List.of(List.of(HEADER.get(0), HEADER.get(2)), List.of(nameOf(testuser2Identity), YES));

    displayEmail(false, true);
    assertEquals("an inactive property has no column", withoutEmail, lines(export(event.getId(), null, testuser1Identity)));

    displayEmail(true, false);
    assertEquals("an invisible property has no column", withoutEmail, lines(export(event.getId(), null, testuser1Identity)));

    displayEmail(true, true);
    assertEquals("a displayed property has its column",
                 List.of(HEADER, row(testuser2Identity, YES)),
                 lines(export(event.getId(), null, testuser1Identity)));
  }

  /**
   * A user who hid their email reads Hidden in its cell, whoever exports; the
   * others keep theirs.
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void aUserWhoHidTheirEmailReadsHidden() throws Exception {
    Event event = spaceEvent(false, testuser2Identity, testuser3Identity);
    profilePropertyService.hidePropertySetting(id(testuser2Identity), emailSettingId);
    hiddenFor.add(id(testuser2Identity));

    List<List<String>> lines = lines(export(event.getId(), null, testuser1Identity));

    assertEquals(3, lines.size());
    assertTrue(lines.toString(), lines.contains(List.of(nameOf(testuser2Identity), "agenda.export.hidden", NO_ANSWER)));
    assertTrue("the email hidden is nowhere in the file", lines.stream().noneMatch(line -> line.contains(emailOf(testuser2Identity))));
    assertEquals(row(testuser3Identity, NO_ANSWER), lineOf(lines, testuser3Identity));
  }

  /**
   * Only who may update the event exports it: neither an attendee nor an
   * outsider, while a manager of the space does, and so does an attendee once
   * the event lets its participants modify it.
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void onlyWhoMayUpdateTheEventExportsIt() throws Exception {
    Event event = spaceEvent(false, testuser2Identity, testuser4Identity);
    Identity root = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root");

    assertThrows(IllegalAccessException.class, () -> export(event.getId(), null, testuser2Identity));
    assertThrows(IllegalAccessException.class, () -> export(event.getId(), null, testuser4Identity));
    assertEquals("a manager of the space exports", 3, lines(export(event.getId(), null, root)).size());

    Event editable = spaceEvent(true, testuser2Identity);
    assertEquals("an attendee allowed to modify the event exports",
                 2,
                 lines(export(editable.getId(), null, testuser2Identity)).size());
  }

  /**
   * On a date with its own row, the right is the row's: an attendee whom the
   * series lets modify it exports a date the series serves, not a date whose
   * row does not let them.
   *
   * @throws Exception when the series or the row cannot be created
   */
  @Test
  public void theRightIsReadOnTheDateServed() throws Exception {
    Event series = spaceSeries(true, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    rowStoredAt(series, dates.get(1), EventStatus.CONFIRMED, false, testuser2Identity);

    assertEquals(2, lines(export(series.getId(), dates.get(0), testuser2Identity)).size());
    assertThrows(IllegalAccessException.class, () -> export(series.getId(), dates.get(1), testuser2Identity));
  }

  /**
   * A date poll is not exported, even by its creator: only a confirmed
   * event's list is (D13).
   *
   * @throws Exception when the poll cannot be created
   */
  @Test
  public void aDatePollIsRefused() throws Exception {
    Event poll = datePoll();

    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> export(poll.getId(), null, testuser1Identity));
    assertEquals("agenda.export.datePoll", refusal.getMessage());
  }

  /**
   * An unknown event is not found, nor is a cancelled date, whether named by
   * its series and its date or by its row.
   *
   * @throws Exception when the series or the row cannot be created
   */
  @Test
  public void anUnknownEventOrACancelledDateIsNotFound() throws Exception {
    Event series = futureSeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event cancelled = rowStoredAt(series, dates.get(1), EventStatus.CANCELLED, false, testuser2Identity);

    assertThrows(ObjectNotFoundException.class, () -> export(Long.MAX_VALUE, null, testuser1Identity));
    assertThrows(ObjectNotFoundException.class, () -> export(series.getId(), dates.get(1), testuser1Identity));
    assertThrows(ObjectNotFoundException.class, () -> export(cancelled.getId(), null, testuser1Identity));
  }

  /**
   * A date of a series lists each answer valid at that date: an answer given
   * from the second date on is not the first date's, and the series as a
   * whole reads its answers at its next date.
   *
   * @throws Exception when the series cannot be created or answered
   */
  @Test
  public void aDateOfASeriesListsTheAnswersValidAtThatDate() throws Exception {
    Event series = futureSeries(testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         dates.get(1),
                                                         id(testuser2Identity),
                                                         EventAttendeeResponse.ACCEPTED);

    assertEquals(row(testuser2Identity, NO_ANSWER), lineOf(lines(export(series.getId(), dates.get(0), testuser1Identity)), testuser2Identity));
    assertEquals(row(testuser2Identity, YES), lineOf(lines(export(series.getId(), dates.get(1), testuser1Identity)), testuser2Identity));
    assertEquals(row(testuser3Identity, NO_ANSWER), lineOf(lines(export(series.getId(), dates.get(1), testuser1Identity)), testuser3Identity));
    assertEquals("the series as a whole, read at its next date",
                 row(testuser2Identity, NO_ANSWER),
                 lineOf(lines(export(series.getId(), null, testuser1Identity)), testuser2Identity));
  }

  /**
   * A list down to one row with a range lists it only at the dates of its
   * range: the date filter applies whatever the number of rows.
   *
   * @throws Exception when the series cannot be created or answered
   */
  @Test
  public void aListOfOneRangedRowListsItOnlyAtTheDatesOfItsRange() throws Exception {
    Event series = futureSeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         dates.get(1),
                                                         id(testuser2Identity),
                                                         EventAttendeeResponse.ACCEPTED);
    for (EventAttendee row : attendeeStorage.getEventAttendees(series.getId()).getEventAttendees()) {
      if (row.getFromOccurrenceId() == null) {
        attendeeStorage.removeEventAttendee(row.getId());
      }
    }
    assertEquals("the series holds one row, from its second date", 1, attendeeStorage.getEventAttendees(series.getId()).size());

    assertEquals(List.of(HEADER), lines(export(series.getId(), dates.get(0), testuser1Identity)));
    assertEquals(List.of(HEADER, row(testuser2Identity, YES)), lines(export(series.getId(), dates.get(1), testuser1Identity)));
  }

  /**
   * A date with its own row lists that row's attendees, named by its series
   * and its date or by the row itself; a date with none lists the series'.
   *
   * @throws Exception when the series or the row cannot be created
   */
  @Test
  public void aDateWithItsOwnRowListsThatRow() throws Exception {
    Event series = futureSeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event row = rowStoredAt(series, dates.get(1), EventStatus.CONFIRMED, false, testuser4Identity);

    List<List<String>> ownRow = List.of(HEADER, row(testuser4Identity, NO_ANSWER));
    assertEquals(ownRow, lines(export(series.getId(), dates.get(1), testuser1Identity)));
    assertEquals(ownRow, lines(export(row.getId(), null, testuser1Identity)));
    assertEquals(List.of(HEADER, row(testuser2Identity, NO_ANSWER)), lines(export(series.getId(), dates.get(0), testuser1Identity)));
  }

  /**
   * The date of an all-day series is found west and east of UTC, whether its
   * id is the date written as a UTC midnight or the zone's midnight converted
   * to UTC, as the endpoint's parser delivers an id carrying an offset.
   *
   * @throws Exception when the series or the row cannot be created
   */
  @Test
  public void aDateOfAnAllDaySeriesIsFoundWestAndEastOfUtc() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("America/New_York"), ZoneId.of("Asia/Tokyo"))) {
      Event series = allDaySeries(zone, testuser2Identity);
      List<ZonedDateTime> dates = occurrenceIds(series);
      rowStoredAt(series, dates.get(1), EventStatus.CONFIRMED, false, testuser4Identity);
      ZonedDateTime zoneMidnight = dates.get(1).toLocalDate().atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC);

      List<List<String>> ownRow = List.of(HEADER, row(testuser4Identity, NO_ANSWER));
      assertEquals(zone + ", the date written as a UTC midnight",
                   ownRow,
                   lines(export(series.getId(), dates.get(1), testuser1Identity)));
      assertEquals(zone + ", the zone's midnight", ownRow, lines(export(series.getId(), zoneMidnight, testuser1Identity)));
      assertEquals(zone + ", a date with no row",
                   List.of(HEADER, row(testuser2Identity, NO_ANSWER)),
                   lines(export(series.getId(), dates.get(0), testuser1Identity)));
    }
  }

  /**
   * A date of an all-day series with no row of its own lists the answers valid
   * from that date, whether its id is the date written as a UTC midnight or
   * the zone's midnight: an answer's range starts at the UTC midnight, which
   * the zone's midnight precedes east of UTC.
   *
   * @throws Exception when the series or the answer cannot be stored
   */
  @Test
  public void aComputedDateOfAnAllDaySeriesListsTheAnswersRangedFromIt() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("America/New_York"), ZoneId.of("Asia/Tokyo"))) {
      Event series = allDaySeries(zone, testuser2Identity);
      List<ZonedDateTime> dates = occurrenceIds(series);
      agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                           dates.get(1),
                                                           id(testuser2Identity),
                                                           EventAttendeeResponse.ACCEPTED);
      ZonedDateTime zoneMidnight = dates.get(1).toLocalDate().atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC);

      List<List<String>> accepted = List.of(HEADER, row(testuser2Identity, YES));
      assertEquals(zone + ", the date written as a UTC midnight",
                   accepted,
                   lines(export(series.getId(), dates.get(1), testuser1Identity)));
      assertEquals(zone + ", the zone's midnight", accepted, lines(export(series.getId(), zoneMidnight, testuser1Identity)));
      assertEquals(zone + ", the date before",
                   List.of(HEADER, row(testuser2Identity, NO_ANSWER)),
                   lines(export(series.getId(), dates.get(0), testuser1Identity)));
    }
  }

  /**
   * A space is named as the drawer names it, by its display name: its profile
   * keeps its former name once a space with an avatar of its own is renamed.
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void aSpaceIsNamedByItsDisplayNameRatherThanItsProfile() throws Exception {
    Profile profile = identityManager.getProfile(spaceIdentity);
    String fullName = profile.getFullName();
    profile.setProperty(Profile.FULL_NAME, "Former space name");
    identityManager.updateProfile(profile);
    try {
      Event event = spaceEvent(false, spaceIdentity);

      assertEquals(List.of(HEADER, List.of(space.getDisplayName(), "", NO_ANSWER)),
                   lines(export(event.getId(), null, testuser1Identity)));
    } finally {
      profile.setProperty(Profile.FULL_NAME, fullName);
      identityManager.updateProfile(profile);
    }
  }

  /**
   * The file is dated with the day of the export in the user's zone: two
   * zones twenty-five hours apart never share a day, and one of them never
   * shares UTC's.
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void theFileIsDatedWithTheDayOfTheExportInTheUserZone() throws Exception {
    Event event = spaceEvent(false, testuser2Identity);
    for (ZoneId zone : List.of(ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Pacific/Pago_Pago"))) {
      String before = LocalDate.now(zone).format(DateTimeFormatter.BASIC_ISO_DATE);
      String fileName = agendaEventAttendeeService.exportEventAttendees(event.getId(),
                                                                       null,
                                                                       id(testuser1Identity),
                                                                       Locale.ENGLISH,
                                                                       zone)
                                                  .fileName();
      String after = LocalDate.now(zone).format(DateTimeFormatter.BASIC_ISO_DATE);
      assertTrue(zone + ": " + fileName,
                 fileName.equals(before + "_eventSummary_participants list.csv")
                     || fileName.equals(after + "_eventSummary_participants list.csv"));
    }
  }

  /**
   * The header and the answers are labelled in the locale of the user who
   * exports.
   *
   * @throws Exception when the event cannot be created or answered
   */
  @Test
  public void theLabelsAreReadInTheLocaleOfTheUserWhoExports() throws Exception {
    Event event = spaceEvent(false, testuser2Identity);
    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    AgendaEventAttendeeServiceImpl service = spy((AgendaEventAttendeeServiceImpl) agendaEventAttendeeService);
    doAnswer(invocation -> invocation.getArgument(0) + ":" + invocation.getArgument(1)).when(service)
                                                                                     .exportLabel(any(), anyString());

    List<List<String>> lines = lines(service.exportEventAttendees(event.getId(),
                                                                   null,
                                                                   id(testuser1Identity),
                                                                   Locale.FRENCH,
                                                                   EXPORT_DAY));

    assertEquals(HEADER.stream().map(key -> "fr:" + key).toList(), lines.get(0));
    assertEquals(List.of(nameOf(testuser2Identity), emailOf(testuser2Identity), "fr:" + YES), lines.get(1));
  }

  private EventAttendeesExport export(long eventId, ZonedDateTime date, Identity exporter) throws Exception {
    return ((AgendaEventAttendeeServiceImpl) agendaEventAttendeeService).exportEventAttendees(eventId,
                                                                                              date,
                                                                                              id(exporter),
                                                                                              Locale.ENGLISH,
                                                                                              EXPORT_DAY);
  }

  /**
   * Reads the file back as its lines of cells: no cell of these tests holds a
   * comma, a quote or a line break.
   */
  private static List<List<String>> lines(EventAttendeesExport export) {
    String content = export.content();
    assertTrue("the file starts with its byte order mark", content.startsWith(AttendeesCsv.BYTE_ORDER_MARK));
    List<List<String>> lines = new ArrayList<>();
    for (String line : content.substring(1).split(AttendeesCsv.LINE_SEPARATOR)) {
      lines.add(Arrays.asList(line.substring(1, line.length() - 1).split("\",\"", -1)));
    }
    return lines;
  }

  /**
   * The line of a user, found by their email: every test user has the same
   * name and an email of their own.
   */
  private List<String> lineOf(List<List<String>> lines, Identity identity) {
    String email = emailOf(identity);
    assertTrue("the test user has an email", StringUtils.isNotBlank(email));
    List<List<String>> found = lines.stream().filter(line -> line.size() > 1 && line.get(1).equals(email)).toList();
    assertEquals("one line for " + email + " in " + lines, 1, found.size());
    return found.get(0);
  }

  private List<String> row(Identity identity, String answer) {
    return List.of(nameOf(identity), emailOf(identity), answer);
  }

  private String nameOf(Identity identity) {
    return identityManager.getIdentity(id(identity)).getProfile().getFullName();
  }

  private String emailOf(Identity identity) {
    return StringUtils.defaultString(identityManager.getIdentity(id(identity)).getProfile().getEmail());
  }

  private void displayEmail(boolean active, boolean visible) {
    ProfilePropertySetting setting = profilePropertyService.getProfileSettingById(emailSettingId);
    setting.setActive(active);
    setting.setVisible(visible);
    profilePropertyService.updatePropertySetting(setting);
  }

  /**
   * An event of no series, starting in sixty days, open in the space's
   * calendar with the capacity given, created by the first test user.
   */
  private Event openLimitedEvent(int capacity, Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(capacity);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * An event of no series, starting in sixty days, in the space's calendar,
   * created by the first test user, who is not one of its attendees.
   */
  private Event spaceEvent(boolean allowAttendeeToUpdate, Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(spaceCalendar.getId());
    event.setAllowAttendeeToUpdate(allowAttendeeToUpdate);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A daily series of three dates starting in sixty days, in the space's
   * calendar, created by the first test user.
   */
  private Event spaceSeries(boolean allowAttendeeToUpdate, Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    event.setAllowAttendeeToUpdate(allowAttendeeToUpdate);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A daily series of three dates starting in sixty days, in the first test
   * user's calendar, who is not one of its attendees.
   */
  private Event futureSeries(Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    return createEvent(newEventInstance(start, start.plusHours(1), false), id(testuser1Identity), attendees);
  }

  /**
   * A daily all-day series of three dates starting in sixty days, in a zone.
   */
  private Event allDaySeries(ZoneId zone, Identity... attendees) throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atStartOfDay(zone);
    Event event = newEventInstance(start, start, true);
    event.setTimeZoneId(zone);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A row of a series for one of its dates, with the status and the modify
   * permission given, inviting the attendees given.
   */
  private Event rowStoredAt(Event series,
                            ZonedDateTime occurrenceId,
                            EventStatus status,
                            boolean allowAttendeeToUpdate,
                            Identity... attendees) throws Exception {
    Event row = agendaEventStorage.getEventById(series.getId());
    row.setId(0);
    row.setParentId(series.getId());
    row.setRecurrence(null);
    row.setOccurrence(new EventOccurrence(occurrenceId, true, false));
    row.setStatus(status);
    row.setAllowAttendeeToUpdate(allowAttendeeToUpdate);
    Event storedRow = agendaEventStorage.createEvent(row);
    agendaEventAttendeeService.saveEventAttendees(storedRow,
                                                  Arrays.stream(attendees)
                                                        .map(attendee -> new EventAttendee(0,
                                                                                           storedRow.getId(),
                                                                                           id(attendee),
                                                                                           EventAttendeeResponse.NEEDS_ACTION))
                                                        .toList(),
                                                  0,
                                                  false,
                                                  false,
                                                  null);
    return storedRow;
  }

  /**
   * A date poll of the space calendar with two dates, created by the first
   * test user, who invites the second one.
   */
  private Event datePoll() throws Exception {
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);
    event.setRecurrence(recurrence);
    ZonedDateTime start = getDate();
    List<EventDateOption> dateOptions = Arrays.asList(new EventDateOption(0, 0, start, start, false, false, null),
                                                      new EventDateOption(0,
                                                                          0,
                                                                          start.plusDays(1),
                                                                          start.plusDays(1),
                                                                          true,
                                                                          true,
                                                                          null));
    return agendaEventService.createEvent(event,
                                          Arrays.asList(new EventAttendee(0,
                                                                          id(testuser1Identity),
                                                                          EventAttendeeResponse.NEEDS_ACTION),
                                                        new EventAttendee(0,
                                                                          id(testuser2Identity),
                                                                          EventAttendeeResponse.NEEDS_ACTION)),
                                          Collections.emptyList(),
                                          Collections.emptyList(),
                                          dateOptions,
                                          null,
                                          true,
                                          id(testuser1Identity));
  }

  /**
   * The occurrence ids of a series' dates, as the occurrence list gives them.
   */
  private List<ZonedDateTime> occurrenceIds(Event series) {
    Event stored = agendaEventService.getEventById(series.getId());
    ZonedDateTime start = stored.getStart();
    return agendaEventService.getEventOccurrencesInPeriod(stored, start.minusDays(1), start.plusDays(5), stored.getTimeZoneId(), 0)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .collect(Collectors.toList());
  }

  private void answer(long eventId, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendEventResponse(eventId, id(identity), response);
  }

  /**
   * Writes the instant an attendee's answer was stored, which orders the
   * waiting list, so that the order does not depend on the clock.
   */
  private void setResponseDate(long eventId, Identity identity, ZonedDateTime responseDate) {
    long rowId = attendeeStorage.getEventAttendees(eventId, id(identity)).getEventAttendees().get(0).getId();
    inOtherTransaction(entityManager -> entityManager.createQuery("UPDATE AgendaEventAttendee a SET a.responseDate = :date WHERE a.id = :id")
                                                     .setParameter("date", Date.from(responseDate.toInstant()))
                                                     .setParameter("id", rowId)
                                                     .executeUpdate());
  }

  private void inOtherTransaction(Consumer<EntityManager> write) {
    EntityManager other = entityManagerService.getEntityManagerFactory().createEntityManager();
    try {
      other.getTransaction().begin();
      write.accept(other);
      other.getTransaction().commit();
    } finally {
      other.close();
    }
  }

  private static long id(Identity identity) {
    return Long.parseLong(identity.getId());
  }

}
