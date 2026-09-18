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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.CalendarShareSource;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarShare;
import org.exoplatform.agenda.model.ChannelDelivery;
import org.exoplatform.agenda.model.ChannelShares;
import org.exoplatform.agenda.model.ExternalShare;
import org.exoplatform.agenda.plugin.CalendarShareChannelPlugin;
import org.exoplatform.agenda.storage.CalendarShareStorage;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Pins every rule of calendar sharing on the service that holds them
 * (EXO-90357): who shares what with whom, in which order the refusals come,
 * and how a delivery channel is composed with the eXo record.
 * <p>
 * The storage is an in-memory stand-in rather than a mock, so a share really
 * exists for the reads that follow it; the channel is a scripted plugin the
 * tests hand to the Spring context stand-in.
 */
class AgendaCalendarShareServiceTest {

  private static final long        OWNER        = 1;

  private static final long        ALICE        = 2;

  private static final long        DISABLED     = 3;

  private static final long        SPACE        = 100;

  private static final long        PERSONAL_CAL = 10;

  private static final long        SPACE_CAL    = 20;

  private static final long        SUBSCRIBED   = 30;

  private final Map<String, Identity> identities = new HashMap<>();

  private InMemoryShareStorage       storage;

  private AgendaCalendarService      calendarService;

  private ListenerService            listenerService;

  private ScriptedChannel            channel;

  private ApplicationContext         applicationContext;

  private AgendaCalendarShareServiceImpl service;

  /**
   * Builds the owner's personal calendar, a space calendar, a subscribed
   * calendar, and the service over them with one scripted channel.
   */
  @BeforeEach
  void setUp() {
    identities.clear();
    user(OWNER, "owner", true);
    user(ALICE, "alice", true);
    user(DISABLED, "disabled", false);
    Identity space = new Identity(SpaceIdentityProvider.NAME, "space");
    space.setId(String.valueOf(SPACE));
    identities.put(String.valueOf(SPACE), space);
    IdentityManager identityManager = mock(IdentityManager.class);
    when(identityManager.getIdentity(anyString())).thenAnswer(invocation -> identities.get(invocation.<String> getArgument(0)));
    when(identityManager.getOrCreateUserIdentity(anyString())).thenAnswer(invocation -> identities.values()
                                                                                                   .stream()
                                                                                                   .filter(Identity::isUser)
                                                                                                   .filter(identity -> identity.getRemoteId()
                                                                                                                               .equals(invocation.getArgument(0)))
                                                                                                   .findFirst()
                                                                                                   .orElse(null));
    calendarService = mock(AgendaCalendarService.class);
    when(calendarService.getCalendarById(PERSONAL_CAL)).thenAnswer(invocation -> calendar(PERSONAL_CAL, OWNER, false));
    when(calendarService.getCalendarById(SPACE_CAL)).thenAnswer(invocation -> calendar(SPACE_CAL, SPACE, false));
    when(calendarService.getCalendarById(SUBSCRIBED)).thenAnswer(invocation -> calendar(SUBSCRIBED, OWNER, true));
    listenerService = mock(ListenerService.class);
    storage = new InMemoryShareStorage();
    channel = new ScriptedChannel();
    applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeansOfType(CalendarShareChannelPlugin.class)).thenReturn(Map.of("caldav", channel));
    service = new AgendaCalendarShareServiceImpl(storage, calendarService, identityManager, listenerService, applicationContext);
  }

  /**
   * The owner shares their personal calendar with a colleague: a record is
   * written, the platform is told, and the channel that says it is none of
   * its business leaves the record eXo-only.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void theOwnerSharesAPersonalCalendar() throws Exception {
    channel.answer = ChannelDelivery.notApplicable();

    CalendarShare share = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    assertEquals(ALICE, share.getShareeIdentityId());
    assertEquals(CalendarShareSource.EXO, share.getSource());
    assertNull(share.getDeliveredTo());
    assertTrue(service.isSharedWith(PERSONAL_CAL, ALICE));
    assertEquals(List.of(PERSONAL_CAL), service.getSharedCalendarIds(ALICE));
    verify(listenerService).broadcast(eq(AgendaCalendarShareService.CALENDAR_SHARED_EVENT), any(CalendarShare.class), eq(OWNER));
  }

  /**
   * The refusals come in the contract's order: an unknown calendar before
   * the owner check, the owner check before the sharee's validity.
   */
  @Test
  void theRefusalsComeInOrder() {
    assertThrows(ObjectNotFoundException.class, () -> service.share(99, "nobody", CalendarShareLevel.VIEW, "alice"), "missing calendar first, even for a stranger");
    assertThrows(IllegalAccessException.class, () -> service.share(PERSONAL_CAL, "nobody", CalendarShareLevel.VIEW, "alice"), "then the owner check, before the sharee");
    IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> service.share(PERSONAL_CAL, "nobody", CalendarShareLevel.VIEW, "owner"));
    assertEquals(AgendaCalendarShareService.SHAREE_UNKNOWN, unknown.getMessage());
    IllegalArgumentException self = assertThrows(IllegalArgumentException.class, () -> service.share(PERSONAL_CAL, "owner", CalendarShareLevel.VIEW, "owner"));
    assertEquals(AgendaCalendarShareService.SHAREE_IS_OWNER, self.getMessage());
    IllegalArgumentException disabled = assertThrows(IllegalArgumentException.class, () -> service.share(PERSONAL_CAL, "disabled", CalendarShareLevel.VIEW, "owner"));
    assertEquals(AgendaCalendarShareService.SHAREE_DISABLED, disabled.getMessage());
    assertThrows(IllegalArgumentException.class, () -> service.share(0, "alice", CalendarShareLevel.VIEW, "owner"));
    assertTrue(storage.rows.isEmpty(), "no refusal writes a row");
  }

  /**
   * Neither a space calendar nor a subscribed calendar is shareable in this
   * version, whoever asks.
   */
  @Test
  void spaceAndSubscribedCalendarsAreNotShareable() {
    assertThrows(IllegalAccessException.class, () -> service.share(SPACE_CAL, "alice", CalendarShareLevel.VIEW, "owner"));
    assertThrows(IllegalAccessException.class, () -> service.share(SUBSCRIBED, "alice", CalendarShareLevel.VIEW, "owner"));
    assertTrue(storage.rows.isEmpty());
  }

  /**
   * A channel that delivers is recorded on the row, with its reference.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void aDeliveringChannelIsRecordedOnTheRow() throws Exception {
    channel.answer = ChannelDelivery.delivered("caldav:1", "/calendars/alice/shared-10/");

    CalendarShare share = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    assertEquals("caldav:1", share.getDeliveredTo());
    assertEquals("/calendars/alice/shared-10/", share.getDeliveryRef());
    assertEquals("caldav:1", storage.rows.get(0).getDeliveredTo());
  }

  /**
   * A channel that fails leaves the record standing, eXo-only and not
   * delivered, with nothing reported to the owner; sharing again asks the
   * channels again without writing a second row, and a delivery that then
   * succeeds records the channel. Nothing is retried on its own.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void aFailedDeliveryKeepsTheRecordUndeliveredAndSilent() throws Exception {
    channel.answer = ChannelDelivery.failed("SERVER_UNREACHABLE");

    CalendarShare share = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    assertNull(share.getDeliveredTo());
    assertTrue(service.isSharedWith(PERSONAL_CAL, ALICE), "the eXo record stands on a server failure");
    assertEquals(1, storage.rows.size());
    assertNull(storage.rows.get(0).getDeliveredTo(), "so a delivery can be attempted later");

    assertNull(service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner").getDeliveredTo());
    assertEquals(1, storage.rows.size(), "sharing again writes no second row");
    assertEquals(2, channel.deliveries, "and asks the channel again");

    channel.answer = ChannelDelivery.delivered("caldav:1", null);
    assertEquals("caldav:1", service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner").getDeliveredTo());
    assertEquals("caldav:1", storage.rows.get(0).getDeliveredTo());
  }

  /**
   * A channel that throws is read as a failure: the record stands, not
   * delivered.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void aThrowingChannelIsReadAsAFailure() throws Exception {
    channel.failure = new IllegalStateException("server down");

    CalendarShare share = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    assertNull(share.getDeliveredTo());
    assertTrue(service.isSharedWith(PERSONAL_CAL, ALICE));
  }

  /**
   * Revoking withdraws from the channel first, then deletes the record; a
   * channel that cannot withdraw does not keep the record alive. Unsharing a
   * colleague without a record succeeds silently.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void revokingWithdrawsThenDeletesWhateverTheChannelAnswers() throws Exception {
    channel.answer = ChannelDelivery.delivered("caldav:1", null);
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");
    channel.withdrawAnswer = false;

    service.unshare(PERSONAL_CAL, ALICE, "owner");

    assertEquals(1, channel.withdrawals, "the channel is asked to withdraw a share it carries");
    assertFalse(service.isSharedWith(PERSONAL_CAL, ALICE), "and the record goes even when it could not");
    verify(listenerService).broadcast(eq(AgendaCalendarShareService.CALENDAR_UNSHARED_EVENT), any(CalendarShare.class), eq(OWNER));

    service.unshare(PERSONAL_CAL, ALICE, "owner");
    assertEquals(1, channel.withdrawals, "nothing to withdraw for a colleague without a record");
    assertThrows(IllegalAccessException.class, () -> service.unshare(PERSONAL_CAL, ALICE, "alice"), "only the owner revokes");
  }

  /**
   * The owner levels a colleague up and down (EXO-90378): the record carries
   * the new level, the platform is told, and the channel is asked to
   * reconcile its grant to it — carrying the level on the share it receives,
   * so the SPI needs no extra parameter.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void theOwnerLevelsAColleagueUpAndDown() throws Exception {
    channel.answer = ChannelDelivery.delivered("caldav:1", "/cal/alice/");
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");
    assertEquals(CalendarShareLevel.VIEW, service.getShareLevel(PERSONAL_CAL, ALICE));
    int delivered = channel.deliveries;

    CalendarShare raised = service.setLevel(PERSONAL_CAL, ALICE, CalendarShareLevel.EDIT, "owner");

    assertEquals(CalendarShareLevel.EDIT, raised.getLevel());
    assertEquals(CalendarShareLevel.EDIT, service.getShareLevel(PERSONAL_CAL, ALICE));
    assertEquals(Map.of(PERSONAL_CAL, CalendarShareLevel.EDIT), service.getShareLevels(ALICE));
    assertEquals(delivered + 1, channel.deliveries, "the channel is asked to match its grant to the new level");
    assertEquals(CalendarShareLevel.EDIT, channel.deliveredLevel, "and the share it receives carries that level");
    verify(listenerService).broadcast(eq(AgendaCalendarShareService.CALENDAR_SHARE_LEVEL_CHANGED_EVENT),
                                      any(CalendarShare.class),
                                      eq(OWNER));

    CalendarShare lowered = service.setLevel(PERSONAL_CAL, ALICE, CalendarShareLevel.VIEW, "owner");

    assertEquals(CalendarShareLevel.VIEW, lowered.getLevel());
    assertEquals(CalendarShareLevel.VIEW, channel.deliveredLevel, "a downgrade narrows the server grant too");
    assertTrue(service.isSharedWith(PERSONAL_CAL, ALICE), "a downgrade is not a revoke");
  }

  /**
   * Only the owner levels, the level is named, and there must be a share to
   * level: the refusals come in the contract's order — calendar, owner,
   * share — and the level check comes before any of them, since a request
   * that names no level names nothing at all.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void onlyTheOwnerLevelsAndOnlyAnExistingShare() throws Exception {
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    IllegalArgumentException noLevel = assertThrows(IllegalArgumentException.class,
                                                    () -> service.setLevel(PERSONAL_CAL, ALICE, null, "owner"));
    assertEquals(AgendaCalendarShareService.LEVEL_MANDATORY, noLevel.getMessage());
    assertThrows(ObjectNotFoundException.class,
                 () -> service.setLevel(99, ALICE, CalendarShareLevel.EDIT, "alice"),
                 "missing calendar first, even for a stranger");
    assertThrows(IllegalAccessException.class,
                 () -> service.setLevel(PERSONAL_CAL, ALICE, CalendarShareLevel.EDIT, "alice"),
                 "an editor levels nobody, themselves least of all");
    assertThrows(ObjectNotFoundException.class,
                 () -> service.setLevel(PERSONAL_CAL, 4, CalendarShareLevel.EDIT, "owner"),
                 "there must be a share to level");
    assertEquals(CalendarShareLevel.VIEW, service.getShareLevel(PERSONAL_CAL, ALICE), "no refusal moves a level");
  }

  /**
   * Sharing again with a colleague who already has a record never widens
   * their level: the second share is a retry of the delivery, and setLevel is
   * the one call that levels.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void sharingAgainNeverWidensALevel() throws Exception {
    channel.answer = ChannelDelivery.notApplicable();
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    CalendarShare again = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.EDIT, "owner");

    assertEquals(CalendarShareLevel.VIEW, again.getLevel(), "the stored level stands");
    assertEquals(CalendarShareLevel.VIEW, service.getShareLevel(PERSONAL_CAL, ALICE));
  }

  /**
   * An eXo-only record asks no channel to withdraw.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void anExoOnlyRecordAsksNoChannelToWithdraw() throws Exception {
    channel.answer = ChannelDelivery.notApplicable();
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    service.unshare(PERSONAL_CAL, ALICE, "owner");

    assertEquals(0, channel.withdrawals);
    assertFalse(service.isSharedWith(PERSONAL_CAL, ALICE));
  }

  /**
   * A read-only grant a channel holds for a colleague is recorded, silently,
   * the moment the owner lists their shares: an adopted share, delivered to
   * the channel, that the server is never asked about, that is recorded once
   * over repeated listings, and that notifies nobody. A grant to a colleague
   * who cannot be shared with — disabled here — stays outside eXo.
   *
   * @throws Exception when the listing is refused
   */
  @Test
  void aReadOnlyGrantToAColleagueIsRecordedOnceAndSilently() throws Exception {
    user(4, "bob", true);
    channel.external = List.of(new ExternalShare("caldav:1", "bob", "EXO_USER", 4, "Bob", true, true, "VIEW", null, "/calendars/owner/shared-10/"),
                               new ExternalShare("caldav:1", "dis", "EXO_USER", DISABLED, "Disabled", true, true, "VIEW", null, "/calendars/owner/shared-10/"));

    List<ExternalShare> external = service.getExternalShares(PERSONAL_CAL, "owner");
    List<ExternalShare> again = service.getExternalShares(PERSONAL_CAL, "owner");

    assertEquals(List.of("dis"), external.stream().map(ExternalShare::getExternalId).toList(), "the disabled colleague's grant stays outside eXo");
    assertEquals(List.of("dis"), again.stream().map(ExternalShare::getExternalId).toList());
    assertEquals(1, storage.rows.size(), "recorded once over two listings");
    CalendarShare adopted = storage.rows.get(0);
    assertEquals(4, adopted.getShareeIdentityId());
    assertEquals(CalendarShareSource.ADOPTED, adopted.getSource());
    assertEquals("caldav:1", adopted.getDeliveredTo());
    assertEquals("/calendars/owner/shared-10/", adopted.getDeliveryRef());
    assertEquals(0, channel.deliveries, "the server is not touched");
    assertTrue(service.isSharedWith(PERSONAL_CAL, 4), "from then on a native share");
    verify(listenerService, never()).broadcast(eq(AgendaCalendarShareService.CALENDAR_SHARED_EVENT), any(), any());
  }

  /**
   * A grant a channel reports as an edit share — one of exactly the shape eXo
   * writes — is adopted as an EDIT record (EXO-90378); one it reports as
   * anything else stays outside eXo, however removable it says it is, because
   * a record whose level eXo cannot reproduce would lie about what the server
   * allows.
   *
   * @throws Exception when the listing is refused
   */
  @Test
  void anEditShapedGrantIsAdoptedAtEditAndAWiderOneIsNot() throws Exception {
    user(4, "bob", true);
    user(5, "carol", true);
    channel.external = List.of(new ExternalShare("caldav:1", "bob", "EXO_USER", 4, "Bob", true, false, "EDIT", null, "/c/"),
                               new ExternalShare("caldav:1", "carol", "EXO_USER", 5, "Carol", true, false, "MORE", null, "/c/"));

    List<ExternalShare> external = service.getExternalShares(PERSONAL_CAL, "owner");

    assertEquals(List.of("carol"), external.stream().map(ExternalShare::getExternalId).toList());
    assertEquals(1, storage.rows.size());
    assertEquals(4, storage.rows.get(0).getShareeIdentityId());
    assertEquals(CalendarShareLevel.EDIT, storage.rows.get(0).getLevel(), "adopted at the level the server holds");
    assertEquals(CalendarShareSource.ADOPTED, storage.rows.get(0).getSource());
    assertEquals(0, channel.deliveries, "the server is not touched");
  }

  /**
   * A channel that carried the share at a narrower level than the record asks
   * for (BlueMind, until its write grant is proved) delivers all the same: the
   * record keeps the owner's level, which is what decides every right in eXo.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void aChannelCarryingANarrowerLevelStillDelivers() throws Exception {
    channel.answer = ChannelDelivery.delivered("caldav:1", "/c/", CalendarShareLevel.VIEW);

    CalendarShare share = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.EDIT, "owner");

    assertEquals(CalendarShareLevel.EDIT, share.getLevel(), "the eXo level is the owner's choice, not the server's");
    assertEquals("caldav:1", share.getDeliveredTo(), "and the share is delivered, not failed");
    assertEquals(CalendarShareLevel.EDIT, service.getShareLevel(PERSONAL_CAL, ALICE));
  }

  /**
   * The external shares are read live from the channels, the recorded sharees
   * left out, and a channel that throws empties nothing but its own answer.
   *
   * @throws Exception when the listing is refused
   */
  @Test
  void externalSharesAreReadLiveWithoutTheRecordedSharees() throws Exception {
    channel.answer = ChannelDelivery.delivered("caldav:1", null);
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");
    channel.external = List.of(new ExternalShare("caldav:1", "grant-alice", "EXO_USER", ALICE, "Alice", true, true, "VIEW", null, "/c/"),
                               new ExternalShare("caldav:1", "grant-bob", "EXO_USER", 4, "Bob", false, false, "MORE", null, "/c/"),
                               new ExternalShare(null, "grant-out", "OUTSIDE_EXO", 0, "someone@else.org", true, true));

    List<ExternalShare> external = service.getExternalShares(PERSONAL_CAL, "owner");

    assertEquals(List.of("grant-bob", "grant-out"), external.stream().map(ExternalShare::getExternalId).toList(),
                 "a colleague holding more than reading, and someone outside eXo, are access held outside eXo");
    assertEquals("caldav:1", external.get(0).getChannelId(), "the channel's qualified id, naming the server, is kept");
    assertEquals("caldav", external.get(1).getChannelId(), "a row without one gets the channel's bare id");
    assertEquals(1, storage.rows.size(), "nothing recorded: alice already is, bob can edit, the other is no user");
    assertThrows(IllegalAccessException.class, () -> service.getExternalShares(PERSONAL_CAL, "alice"));

    channel.failure = new IllegalStateException("server down");
    assertTrue(service.getExternalShares(PERSONAL_CAL, "owner").isEmpty());
  }

  /**
   * A sharee hides a calendar and shows it again without the share going
   * anywhere; a calendar not shared with them cannot be hidden.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void hidingNeverDeletesTheShare() throws Exception {
    channel.answer = ChannelDelivery.notApplicable();
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    service.setHidden(PERSONAL_CAL, "alice", true);

    assertTrue(service.getSharedWithMe("alice").get(0).isHidden());
    assertTrue(service.isSharedWith(PERSONAL_CAL, ALICE), "hidden is not revoked");
    assertEquals(1, service.getShares(PERSONAL_CAL, "owner").size(), "the owner still lists the colleague");
    service.setHidden(PERSONAL_CAL, "alice", false);
    assertFalse(service.getSharedWithMe("alice").get(0).isHidden());
    assertThrows(ObjectNotFoundException.class, () -> service.setHidden(SPACE_CAL, "alice", true));
  }

  /**
   * The listing for a sharee leaves out a share whose calendar is gone or
   * whose owner is gone.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void theSharedWithMeListingLeavesOutDeadCalendars() throws Exception {
    channel.answer = ChannelDelivery.notApplicable();
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");
    storage.rows.add(new CalendarShare(9, 77, ALICE, CalendarShareLevel.VIEW, OWNER, 0, CalendarShareSource.EXO, null, null, false));
    when(calendarService.getCalendarById(77L)).thenReturn(null);

    assertEquals(List.of(PERSONAL_CAL), service.getSharedWithMe("alice").stream().map(CalendarShare::getCalendarId).toList());

    identities.get(String.valueOf(OWNER)).setDeleted(true);
    assertTrue(service.getSharedWithMe("alice").isEmpty(), "a deleted owner's calendar is not listed");
  }

  /**
   * The counts and the cleanups delegate to the storage with the identities
   * they name, and a disconnect only clears the delivery.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void countsAndCleanupsNameTheirIdentities() throws Exception {
    channel.answer = ChannelDelivery.delivered("caldav:1", null);
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    assertEquals(Map.of(PERSONAL_CAL, 1L), service.countShareesByCalendar("owner"));

    service.clearDelivery(OWNER, "caldav:1");
    assertNull(storage.rows.get(0).getDeliveredTo());
    assertTrue(service.isSharedWith(PERSONAL_CAL, ALICE), "the eXo share stays after a disconnect");

    service.deleteSharesOfUser(ALICE);
    assertFalse(service.isSharedWith(PERSONAL_CAL, ALICE));
    service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");
    service.deleteShares(PERSONAL_CAL);
    assertFalse(service.isSharedWith(PERSONAL_CAL, ALICE));
    verify(applicationContext, never()).getBean(anyString());
  }

  /**
   * Without any channel installed, sharing is eXo-only and everything else
   * still works.
   *
   * @throws Exception when the share is refused
   */
  @Test
  void withoutAChannelSharingIsExoOnly() throws Exception {
    when(applicationContext.getBeansOfType(CalendarShareChannelPlugin.class)).thenReturn(Map.of());

    CalendarShare share = service.share(PERSONAL_CAL, "alice", CalendarShareLevel.VIEW, "owner");

    assertNull(share.getDeliveredTo());
    assertTrue(service.getExternalShares(PERSONAL_CAL, "owner").isEmpty());
    service.unshare(PERSONAL_CAL, ALICE, "owner");
    assertFalse(service.isSharedWith(PERSONAL_CAL, ALICE));
  }

  /**
   * Whether the calendar holds meeting copies is the channels' word, owner
   * only: any channel saying so is enough, a channel that throws says nothing,
   * and no channel means no copies.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void meetingCopiesAreTheChannelsWord() throws Exception {
    assertFalse(service.holdsMeetingCopies(PERSONAL_CAL, "owner"));
    channel.meetingCopies = true;
    assertTrue(service.holdsMeetingCopies(PERSONAL_CAL, "owner"));
    assertThrows(IllegalAccessException.class, () -> service.holdsMeetingCopies(PERSONAL_CAL, "alice"));
    assertThrows(ObjectNotFoundException.class, () -> service.holdsMeetingCopies(99, "owner"));
    channel.failure = new IllegalStateException("server down");
    assertFalse(service.holdsMeetingCopies(PERSONAL_CAL, "owner"), "a channel that cannot answer copies nothing");
    when(applicationContext.getBeansOfType(CalendarShareChannelPlugin.class)).thenReturn(Map.of());
    assertFalse(service.holdsMeetingCopies(PERSONAL_CAL, "owner"));
  }

  /**
   * <b>One ask per channel per opening of the drawer</b> (EXO-90385). What the
   * Share drawer reads — the access held outside eXo and the meeting-copies
   * warning — comes from a single call to each channel, so a channel that can
   * answer both from one read of its server is never made to read twice. The
   * channel here answers both itself; the owner check and the adoption of a
   * read-only grant are unchanged.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void theDrawersAskIsOneCallPerChannel() throws Exception {
    OneReadChannel single = new OneReadChannel();
    single.external = List.of(new ExternalShare("caldav:1", "grant-9", "OUTSIDE_EXO", 0, "x@y.org", true, true));
    single.meetingCopies = true;
    when(applicationContext.getBeansOfType(CalendarShareChannelPlugin.class)).thenReturn(Map.of("single", single));

    ChannelShares answer = service.getChannelShares(PERSONAL_CAL, "owner");

    assertEquals(1, answer.shares().size());
    assertEquals("grant-9", answer.shares().get(0).getExternalId());
    assertTrue(answer.meetingCopies(), "the flag the one read carried");
    assertEquals(1, single.listCalls, "one ask");
    assertEquals(0, single.separateCalls, "and neither of the two it stands for");
    assertThrows(IllegalAccessException.class, () -> service.getChannelShares(PERSONAL_CAL, "alice"));
    assertThrows(ObjectNotFoundException.class, () -> service.getChannelShares(99, "owner"));
  }

  /**
   * A channel written before EXO-90385 — one that does not override
   * {@code listShares} — still answers both, through the SPI's default, and
   * agenda reads it exactly as it did.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void aChannelThatDoesNotOverrideTheOneAskStillAnswersBoth() throws Exception {
    channel.external = List.of(new ExternalShare("caldav:1", "grant-9", "OUTSIDE_EXO", 0, "x@y.org", true, true));
    channel.meetingCopies = true;

    ChannelShares answer = service.getChannelShares(PERSONAL_CAL, "owner");

    assertEquals(1, answer.shares().size());
    assertTrue(answer.meetingCopies());
  }

  /**
   * Registers a user identity.
   *
   * @param id identity identifier
   * @param username username
   * @param enabled whether enabled
   */
  private void user(long id, String username, boolean enabled) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    identity.setEnable(enabled);
    identities.put(String.valueOf(id), identity);
  }

  /**
   * A calendar.
   *
   * @param id calendar identifier
   * @param ownerId owner identity identifier
   * @param subscription whether subscribed
   * @return the calendar
   */
  private static Calendar calendar(long id, long ownerId, boolean subscription) {
    Calendar calendar = new Calendar();
    calendar.setId(id);
    calendar.setOwnerId(ownerId);
    calendar.setSubscription(subscription);
    return calendar;
  }

  /**
   * A channel answering what the test scripted.
   */
  private static class ScriptedChannel implements CalendarShareChannelPlugin {

    ChannelDelivery     answer         = ChannelDelivery.notApplicable();

    RuntimeException    failure;

    boolean             withdrawAnswer = true;


    List<ExternalShare> external       = List.of();

    boolean             meetingCopies;

    int                 deliveries;

    int                 withdrawals;

    /** The level of the last share the channel was asked to carry (EXO-90378). */
    CalendarShareLevel  deliveredLevel;

    /**
     * The bare channel id: its deliveries say {@code caldav:1}, the server,
     * and the service must still find this channel for them by that prefix.
     *
     * @return {@code caldav}
     */
    @Override
    public String id() {
      return "caldav";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChannelDelivery deliver(CalendarShare share, String ownerUsername) {
      deliveries++;
      deliveredLevel = share.getLevel();
      if (failure != null) {
        throw failure;
      }
      return answer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean withdraw(CalendarShare share, String ownerUsername) {
      withdrawals++;
      return withdrawAnswer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ExternalShare> listExternalShares(long calendarId, String ownerUsername, List<Long> recordedShareeIds) {
      if (failure != null) {
        throw failure;
      }
      return external;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeExternalShare(long calendarId, String externalId, String ownerUsername) {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean holdsMeetingCopies(long calendarId, String ownerUsername) {
      if (failure != null) {
        throw failure;
      }
      return meetingCopies;
    }
  }

  /**
   * A channel that answers the drawer's two questions from one read
   * (EXO-90385), as the CalDAV one does, and counts what it was asked.
   */
  private static class OneReadChannel implements CalendarShareChannelPlugin {

    List<ExternalShare> external = List.of();

    boolean             meetingCopies;

    /** How many times the one ask was made. */
    int                 listCalls;

    /** How many times either of the two methods it stands for was made. */
    int                 separateCalls;

    /**
     * {@inheritDoc}
     */
    @Override
    public String id() {
      return "one-read";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChannelDelivery deliver(CalendarShare share, String ownerUsername) {
      return ChannelDelivery.notApplicable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean withdraw(CalendarShare share, String ownerUsername) {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ExternalShare> listExternalShares(long calendarId, String ownerUsername, List<Long> recordedShareeIds) {
      separateCalls++;
      return external;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeExternalShare(long calendarId, String externalId, String ownerUsername) {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean holdsMeetingCopies(long calendarId, String ownerUsername) {
      separateCalls++;
      return meetingCopies;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChannelShares listShares(long calendarId, String ownerUsername, List<Long> recordedShareeIds) {
      listCalls++;
      return new ChannelShares(external, meetingCopies);
    }
  }

  /**
   * A storage over a list, with the storage's own contract: no cache, so
   * every read sees the last write.
   */
  private static class InMemoryShareStorage extends CalendarShareStorage {

    final List<CalendarShare> rows = new ArrayList<>();

    private long              nextId = 1;

    /**
     * Builds the stand-in.
     */
    InMemoryShareStorage() {
      super(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getSharedCalendarIds(long viewerIdentityId) {
      return rows.stream().filter(row -> row.getShareeIdentityId() == viewerIdentityId).map(CalendarShare::getCalendarId).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Long, CalendarShareLevel> getShareLevels(long viewerIdentityId) {
      Map<Long, CalendarShareLevel> levels = new HashMap<>();
      rows.stream()
          .filter(row -> row.getShareeIdentityId() == viewerIdentityId)
          .forEach(row -> levels.put(row.getCalendarId(), row.getLevel()));
      return levels;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarShareLevel getShareLevel(long calendarId, long viewerIdentityId) {
      return getShareLevels(viewerIdentityId).get(calendarId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSharedWith(long calendarId, long viewerIdentityId) {
      return getSharedCalendarIds(viewerIdentityId).contains(calendarId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CalendarShare> getShares(long calendarId) {
      return rows.stream().filter(row -> row.getCalendarId() == calendarId).map(CalendarShare::clone).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarShare getShare(long calendarId, long shareeIdentityId) {
      return find(calendarId, shareeIdentityId) == null ? null : find(calendarId, shareeIdentityId).clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CalendarShare> getSharesOfSharee(long shareeIdentityId, int limit) {
      return rows.stream().filter(row -> row.getShareeIdentityId() == shareeIdentityId).map(CalendarShare::clone).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Long, Long> countShareesByCalendar(long ownerIdentityId) {
      Map<Long, Long> counts = new HashMap<>();
      rows.forEach(row -> counts.merge(row.getCalendarId(), 1L, Long::sum));
      return counts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarShare save(long calendarId,
                              long shareeIdentityId,
                              long grantedById,
                              CalendarShareLevel level,
                              CalendarShareSource source,
                              String deliveredTo,
                              String deliveryRef,
                              Date createdDate) {
      CalendarShare existing = find(calendarId, shareeIdentityId);
      if (existing != null) {
        return existing.clone();
      }
      CalendarShare row = new CalendarShare(nextId++,
                                            calendarId,
                                            shareeIdentityId,
                                            level == null ? CalendarShareLevel.VIEW : level,
                                            grantedById,
                                            createdDate.getTime(),
                                            source,
                                            deliveredTo,
                                            deliveryRef,
                                            false);
      rows.add(row);
      return row.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarShare setLevel(long calendarId, long shareeIdentityId, CalendarShareLevel level) {
      CalendarShare row = find(calendarId, shareeIdentityId);
      if (row == null) {
        return null;
      }
      row.setLevel(level == null ? CalendarShareLevel.VIEW : level);
      return row.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarShare setDelivery(long calendarId, long shareeIdentityId, String deliveredTo, String deliveryRef) {
      CalendarShare row = find(calendarId, shareeIdentityId);
      if (row == null) {
        return null;
      }
      row.setDeliveredTo(deliveredTo);
      row.setDeliveryRef(deliveryRef);
      return row.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarShare setHidden(long calendarId, long shareeIdentityId, boolean hidden) {
      CalendarShare row = find(calendarId, shareeIdentityId);
      if (row == null) {
        return null;
      }
      row.setHidden(hidden);
      return row.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(long calendarId, long shareeIdentityId) {
      return rows.removeIf(row -> row.getCalendarId() == calendarId && row.getShareeIdentityId() == shareeIdentityId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int deleteByCalendarId(long calendarId) {
      int before = rows.size();
      rows.removeIf(row -> row.getCalendarId() == calendarId);
      return before - rows.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int deleteOfUser(long identityId) {
      int before = rows.size();
      rows.removeIf(row -> row.getShareeIdentityId() == identityId);
      return before - rows.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int clearDelivery(long ownerIdentityId, String channelId) {
      int cleared = 0;
      for (CalendarShare row : rows) {
        if (channelId.equals(row.getDeliveredTo())) {
          row.setDeliveredTo(null);
          row.setDeliveryRef(null);
          cleared++;
        }
      }
      return cleared;
    }

    /**
     * The row of a pair.
     *
     * @param calendarId calendar identifier
     * @param shareeIdentityId sharee identity identifier
     * @return the row, or null
     */
    private CalendarShare find(long calendarId, long shareeIdentityId) {
      return rows.stream()
                 .filter(row -> row.getCalendarId() == calendarId && row.getShareeIdentityId() == shareeIdentityId)
                 .findFirst()
                 .orElse(null);
    }
  }

}
