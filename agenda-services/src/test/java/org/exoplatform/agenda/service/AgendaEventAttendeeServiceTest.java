/*
 * Copyright (C) 2020 eXo Platform SAS.
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
package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.EventInvitationExpiredException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.exoplatform.web.security.codec.CodecInitializer;

public class AgendaEventAttendeeServiceTest extends BaseAgendaEventTest {

  @Test
  public void testGetEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser5Identity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());

    EventAttendee eventAttendeeToStore = ATTENDEES.get(0);

    EventAttendee eventAttendee = eventAttendees.get(0);
    assertNotNull(eventAttendee);
    assertTrue(eventAttendee.getId() > 0);
    assertEquals(eventAttendeeToStore.getIdentityId(), eventAttendee.getIdentityId());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, eventAttendee.getResponse());
  }

  @Test
  public void testGetEventAttendeesByResponses() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event.clone(),
                        creatorId,
                        testuser1Identity,
                        testuser5Identity,
                        spaceIdentity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId, EventAttendeeResponse.ACCEPTED)
                                                                   .getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());

    EventAttendee eventAttendee = eventAttendees.get(0);
    assertNotNull(eventAttendee);
    assertTrue(eventAttendee.getId() > 0);
    assertEquals(creatorId, eventAttendee.getIdentityId());
    assertEquals(EventAttendeeResponse.ACCEPTED, eventAttendee.getResponse());

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);

    eventAttendees =
                   agendaEventAttendeeService.getEventAttendees(eventId, EventAttendeeResponse.ACCEPTED).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(2, eventAttendees.size());

    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId,
                                                                  EventAttendeeResponse.ACCEPTED,
                                                                  EventAttendeeResponse.TENTATIVE)
                                               .getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(3, eventAttendees.size());
  }

  @Test
  public void testGetEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    try {
      agendaEventAttendeeService.getEventResponse(eventId, null, Long.parseLong(testuser4Identity.getId()));
      fail("should throw an exception, user is not attendee of the event");
    } catch (IllegalAccessException e) {
      // Expected, user is not attendee of the event
    }

    try {
      agendaEventAttendeeService.getEventResponse(5000l, null, Long.parseLong(testuser1Identity.getId()));
      fail("should throw an exception, event id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      null,
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals("Creator should accept event just after creating the event", EventAttendeeResponse.ACCEPTED, eventResponse);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                null,
                                                                Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals("Invitee default response should be empty just after creating the event",
                 EventAttendeeResponse.NEEDS_ACTION,
                 eventResponse);
  }

  @Test
  public void testSendEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    try {
      agendaEventAttendeeService.sendEventResponse(eventId, Long.parseLong(testuser1Identity.getId()), null);
      fail("should throw an exception, response shouldn't be null");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendEventResponse(5000l,
                                                   Long.parseLong(testuser1Identity.getId()),
                                                   EventAttendeeResponse.ACCEPTED);
      fail("should throw an exception, event with id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendEventResponse(0,
                                                   Long.parseLong(testuser1Identity.getId()),
                                                   EventAttendeeResponse.ACCEPTED);
      fail("should throw an exception, event with id doesn't exists");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser1Identity.getId()),
                                                 EventAttendeeResponse.DECLINED);
    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      null,
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);
    eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                null,
                                                                Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.TENTATIVE, eventResponse);
  }

  @Test
  public void testDecryptUserIdentityOfGuestAttendee() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    String guestEmail = "guest.attendee@example.com";
    Identity guestIdentity = identityManager.getOrCreateIdentity(AgendaGuestUserIdentityProvider.NAME, guestEmail);
    assertNotNull("the guest identity provider must be registered", guestIdentity);
    assertEquals(AgendaGuestUserIdentityProvider.NAME, guestIdentity.getProviderId());

    Event event = newEventInstance(start, start, true);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, guestIdentity);
    long eventId = event.getId();

    String token = agendaEventAttendeeService.generateEncryptedToken(eventId, guestEmail, EventAttendeeResponse.ACCEPTED);
    assertNotNull(token);

    Identity decryptedIdentity = agendaEventAttendeeService.decryptUserIdentity(eventId,
                                                                                token,
                                                                                EventAttendeeResponse.ACCEPTED);
    assertNotNull("the token of a guest attendee must resolve to an identity", decryptedIdentity);
    assertEquals("the token of a guest attendee must resolve under the guest provider",
                 AgendaGuestUserIdentityProvider.NAME,
                 decryptedIdentity.getProviderId());
    assertEquals(guestIdentity.getId(), decryptedIdentity.getId());
    assertTrue("the identity resolved from the token must be recognized as an attendee",
               agendaEventAttendeeService.isEventAttendee(eventId, Long.parseLong(decryptedIdentity.getId())));
  }

  @Test
  public void testDecryptUserIdentityOfUnknownAttendeeCreatesNothing() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    Event event = newEventInstance(start, start, true);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    String strangerEmail = "stranger.not.invited@example.com";
    String token = agendaEventAttendeeService.generateEncryptedToken(eventId, strangerEmail, EventAttendeeResponse.ACCEPTED);
    assertNotNull(token);

    Identity decryptedIdentity = agendaEventAttendeeService.decryptUserIdentity(eventId,
                                                                                token,
                                                                                EventAttendeeResponse.ACCEPTED);
    assertNull("a mail address which is not an attendee must not resolve to any identity", decryptedIdentity);

    IdentityStorage identityStorage = container.getComponentInstanceOfType(IdentityStorage.class);
    assertNull("resolving the token must not create a guest identity",
               identityStorage.findIdentity(AgendaGuestUserIdentityProvider.NAME, strangerEmail));
    assertNull("resolving the token must not create an organization identity",
               identityStorage.findIdentity("organization", strangerEmail));
  }

  /**
   * A link for a meeting still to come is honoured, and the same link for a
   * meeting already over is not.
   *
   * <p>
   * The two halves are one test on purpose: asserting only the refusal would
   * pass just as well against a decrypt that refused everything.
   */
  @Test
  public void testInvitationTokenExpiresWithItsEvent() throws Exception { // NOSONAR
    Event futureEvent = createNonRecurringEvent(ZonedDateTime.now().plusDays(1).withNano(0));
    String liveToken = agendaEventAttendeeService.generateEncryptedToken(futureEvent.getId(),
                                                                        testuser5Identity.getRemoteId(),
                                                                        EventAttendeeResponse.ACCEPTED);
    assertNotNull("a meeting still to come must produce a token", liveToken);
    assertNotNull("and that token must resolve to its attendee",
                  agendaEventAttendeeService.decryptUserIdentity(futureEvent.getId(),
                                                                 liveToken,
                                                                 EventAttendeeResponse.ACCEPTED));

    Event pastEvent = createNonRecurringEvent(ZonedDateTime.now().minusDays(10).withNano(0));
    String staleToken = agendaEventAttendeeService.generateEncryptedToken(pastEvent.getId(),
                                                                         testuser5Identity.getRemoteId(),
                                                                         EventAttendeeResponse.ACCEPTED);
    assertNotNull(staleToken);
    try {
      agendaEventAttendeeService.decryptUserIdentity(pastEvent.getId(), staleToken, EventAttendeeResponse.ACCEPTED);
      fail("a token for a meeting that is over must not resolve to anybody");
    } catch (EventInvitationExpiredException e) {
      // Expected: the link outlived its meeting.
    }
  }

  /**
   * The checks that were already there still refuse what they always refused.
   *
   * <p>
   * A regression pin rather than a new assertion: adding an expiry must not make
   * the event and answer checks any weaker, and in particular a token repointed
   * at another meeting or another answer must still be refused as forged - not
   * quietly reclassified as merely expired.
   */
  @Test
  public void testExpiryDoesNotWeakenTheEventAndAnswerChecks() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = createNonRecurringEvent(start);
    Event otherEvent = createNonRecurringEvent(start.plusHours(2));

    String token = agendaEventAttendeeService.generateEncryptedToken(event.getId(),
                                                                     testuser5Identity.getRemoteId(),
                                                                     EventAttendeeResponse.ACCEPTED);
    assertNotNull(token);

    try {
      agendaEventAttendeeService.decryptUserIdentity(otherEvent.getId(), token, EventAttendeeResponse.ACCEPTED);
      fail("a token minted for one meeting must not answer another");
    } catch (EventInvitationExpiredException e) {
      fail("a repointed token must be refused as forged, not reported as expired");
    } catch (IllegalAccessException e) {
      // Expected: wrong event.
    }

    try {
      agendaEventAttendeeService.decryptUserIdentity(event.getId(), token, EventAttendeeResponse.DECLINED);
      fail("a token minted for one answer must not record another");
    } catch (EventInvitationExpiredException e) {
      fail("a repointed token must be refused as forged, not reported as expired");
    } catch (IllegalAccessException e) {
      // Expected: wrong answer.
    }
  }

  /**
   * A token minted before EXO-89752 keeps working while its meeting is live, and
   * stops working once it is over.
   *
   * <p>
   * This is the decision the fix rests on. Every invitation already delivered
   * carries a three field payload with no expiry in it. Refusing those outright
   * would break the Accept button of every mail ever sent; honouring them
   * unchecked would leave the replayable link in place for precisely the
   * population that already holds one. Instead the bound is computed from the
   * event at decrypt time, so an old link lives exactly as long as a new one for
   * the same meeting would - no transition window, no flag to turn off, and no
   * date on which old links stop working in a batch.
   */
  @Test
  public void testLegacyTokenWithNoExpiryIsBoundedByItsEventAllTheSame() throws Exception { // NOSONAR
    Event futureEvent = createNonRecurringEvent(ZonedDateTime.now().plusDays(1).withNano(0));
    Identity resolved = agendaEventAttendeeService.decryptUserIdentity(futureEvent.getId(),
                                                                      legacyToken(futureEvent.getId(),
                                                                                  testuser5Identity.getRemoteId(),
                                                                                  EventAttendeeResponse.ACCEPTED),
                                                                      EventAttendeeResponse.ACCEPTED);
    assertNotNull("an invitation already in somebody's mailbox must keep working while its meeting is live", resolved);
    assertEquals(testuser5Identity.getId(), resolved.getId());

    Event pastEvent = createNonRecurringEvent(ZonedDateTime.now().minusDays(10).withNano(0));
    try {
      agendaEventAttendeeService.decryptUserIdentity(pastEvent.getId(),
                                                     legacyToken(pastEvent.getId(),
                                                                 testuser5Identity.getRemoteId(),
                                                                 EventAttendeeResponse.ACCEPTED),
                                                     EventAttendeeResponse.ACCEPTED);
      fail("an old token is bounded by its event too, or the fix leaves the replayable link in place");
    } catch (EventInvitationExpiredException e) {
      // Expected: the old link is bounded by the same rule, computed instead of
      // read.
    }
  }

  /**
   * The same event and answer always mint the same token.
   *
   * <p>
   * Pinned here because the calendar copy carries these links in its
   * DESCRIPTION and the mirror rewrites any copy whose description changed
   * (EXO-89753). A token that varied between renders would put every copy into
   * permanent churn.
   */
  @Test
  public void testTokenIsByteStableAcrossRenders() throws Exception { // NOSONAR
    Event event = createNonRecurringEvent(ZonedDateTime.now().plusDays(1).withNano(0));

    String first = agendaEventAttendeeService.generateEncryptedToken(event.getId(),
                                                                     testuser5Identity.getRemoteId(),
                                                                     EventAttendeeResponse.ACCEPTED);
    String second = agendaEventAttendeeService.generateEncryptedToken(event.getId(),
                                                                      testuser5Identity.getRemoteId(),
                                                                      EventAttendeeResponse.ACCEPTED);

    assertNotNull(first);
    assertEquals("two renders of the same invitation must produce the very same token", first, second);
  }

  /**
   * Creates a plain one-off event, attended by testuser5, over a one hour slot.
   *
   * <p>
   * The shared factory builds a recurring, all-day event, whose bound is the
   * series end and whose all-day widening pushes it further still. Neither helps
   * when the point is to place a meeting precisely before or after now.
   *
   * @param start when the meeting starts
   * @return the created {@link Event}, as stored
   * @throws Exception when the event cannot be created
   */
  private Event createNonRecurringEvent(ZonedDateTime start) throws Exception { // NOSONAR
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setStatus(EventStatus.CONFIRMED);
    return createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
  }

  /**
   * Mints a token in the shape used before EXO-89752 - three fields, no expiry -
   * by encoding the payload with the platform codec directly.
   *
   * <p>
   * Built by hand rather than by calling the service, because the service is
   * exactly the thing that no longer produces this shape. It is the only way to
   * test against an invitation that is already in somebody's mailbox.
   *
   * @param eventId technical identifier of the event the token answers
   * @param emailOrUsername identifier of the attendee, as the old payload
   *          carried it
   * @param response the answer the link records
   * @return the encoded legacy token
   * @throws Exception when the platform codec cannot be obtained
   */
  private String legacyToken(long eventId, String emailOrUsername, EventAttendeeResponse response) throws Exception { // NOSONAR
    CodecInitializer codecInitializer = container.getComponentInstanceOfType(CodecInitializer.class);
    return codecInitializer.getCodec().encode(eventId + "@@@" + emailOrUsername + "@@@" + response.getValue());
  }

  @Test
  public void testSaveRecurrentEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     start.plusDays(2).toLocalDate(),
                                                     0,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null);
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    long eventId = event.getId();
    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(eventId, start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    long exceptionalOccurrenceId = exceptionalOccurrence.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, eventResponse);

    EventAttendeeResponse exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                                      null,
                                                                                                      userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, exceptionalOccurrenceResponse);

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);
    exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                null,
                                                                                userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.DECLINED, exceptionalOccurrenceResponse);

    agendaEventAttendeeService.sendEventResponse(exceptionalOccurrenceId, userIdentityId, EventAttendeeResponse.TENTATIVE);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);
    exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                null,
                                                                                userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.TENTATIVE, exceptionalOccurrenceResponse);

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.ACCEPTED);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, eventResponse);
    exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                null,
                                                                                userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, exceptionalOccurrenceResponse);
  }

  @Test
  public void testSaveEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser5Identity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());
    EventAttendee eventAttendee = eventAttendees.get(0);

    eventAttendee = eventAttendee.clone();
    eventAttendee.setId(0);
    eventAttendees.add(eventAttendee);

    long userIdentityId = Long.parseLong(testuser5Identity.getId());
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  userIdentityId,
                                                  true,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              userIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals("Same user was added twice, only one attendee object should remain in store", 1, eventAttendees.size());

    eventAttendee = eventAttendee.clone();
    eventAttendee.setId(0);
    eventAttendee.setIdentityId(Long.parseLong(testuser4Identity.getId()));
    eventAttendees.add(eventAttendee);

    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  userIdentityId,
                                                  true,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              userIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(2, eventAttendees.size());

    agendaEventAttendeeService.saveEventAttendees(event,
                                                  Collections.emptyList(),
                                                  userIdentityId,
                                                  true,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              userIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(0, eventAttendees.size());
  }

  @Test
  public void testCreateEventWithResponseOfOtherAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    long creatorIdentityId = Long.parseLong(testuser1Identity.getId());
    long otherIdentityId = Long.parseLong(testuser5Identity.getId());

    List<EventAttendee> attendees = new ArrayList<>();
    attendees.add(new EventAttendee(0, 0, creatorIdentityId, EventAttendeeResponse.ACCEPTED));
    attendees.add(new EventAttendee(0, 0, otherIdentityId, EventAttendeeResponse.ACCEPTED));

    Event event = agendaEventService.createEvent(newEventInstance(start, start, false),
                                                 attendees,
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 false,
                                                 creatorIdentityId);

    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(event.getId(), null, creatorIdentityId));
    assertEquals("A user can only answer an invitation for himself",
                 EventAttendeeResponse.NEEDS_ACTION,
                 agendaEventAttendeeService.getEventResponse(event.getId(), null, otherIdentityId));
  }

  @Test
  public void testSaveEventAttendeesWithResponseOfOtherAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    long creatorIdentityId = Long.parseLong(testuser1Identity.getId());
    long otherIdentityId = Long.parseLong(testuser4Identity.getId());

    Event event = newEventInstance(start, start, false);
    event = createEvent(event.clone(), creatorIdentityId, testuser5Identity);
    long eventId = event.getId();

    List<EventAttendee> attendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    attendees.add(new EventAttendee(0, eventId, creatorIdentityId, EventAttendeeResponse.ACCEPTED));
    attendees.add(new EventAttendee(0, eventId, otherIdentityId, EventAttendeeResponse.ACCEPTED));

    agendaEventAttendeeService.saveEventAttendees(event,
                                                  attendees,
                                                  creatorIdentityId,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(eventId,
                                                                              event.getCalendarId(),
                                                                              creatorIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.UPDATED)));

    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId, null, creatorIdentityId));
    assertEquals("A user can only answer an invitation for himself",
                 EventAttendeeResponse.NEEDS_ACTION,
                 agendaEventAttendeeService.getEventResponse(eventId, null, otherIdentityId));
  }

  @Test
  public void testSendUpcomingEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event.setRecurrence(new EventRecurrence(0,
                                            null,
                                            0,
                                            EventRecurrenceType.DAILY,
                                            EventRecurrenceFrequency.DAILY,
                                            1,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null));

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      null,
                                                                                      Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, eventResponse);

    List<Event> eventOccurrences = agendaEventService.getEventOccurrencesInPeriod(event,
                                                                                  start,
                                                                                  start.plusDays(10),
                                                                                  ZoneOffset.UTC,
                                                                                  0);
    assertNotNull(eventOccurrences);
    assertEquals(11, eventOccurrences.size());

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser1Identity.getId()),
                                                           null);
      fail("should throw an exception, response shouldn't be null");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(0,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser1Identity.getId()),
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, event with id doesn't exists");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           0,
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, occurrence id doesn't exists");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           2222l,
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, identity id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(5000l,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser1Identity.getId()),
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, event with id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser3Identity.getId()),
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, user testuser3 isn't an attendee");
    } catch (IllegalAccessException e) {
      // Expected
    }

    agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                         eventOccurrences.get(3).getOccurrence().getId(),
                                                         Long.parseLong(testuser5Identity.getId()),
                                                         EventAttendeeResponse.TENTATIVE);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(0).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(1).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(2).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(3).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(4).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(9).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));

    agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                         eventOccurrences.get(5).getOccurrence().getId(),
                                                         Long.parseLong(testuser5Identity.getId()),
                                                         EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(0).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(1).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(2).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(3).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(4).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(5).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(9).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(9).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
  }

  /*
   * eXIP 7.3.0.20 (EXO-89479): answering an open event. The gate is
   * AgendaEventAttendeeService#canRespondToEvent, applied by getEventResponse,
   * sendEventResponse and sendUpcomingEventResponse. Fixture: testuser1, 2 and
   * 3 are members of the space owning spaceCalendar; testuser4 and 5 are not.
   */

  @Test
  public void testOpenEventLetsAnySpaceMemberAnswer() throws Exception { // NOSONAR
    Event event = createSpaceEvent(getDate().withNano(0), true, false, testuser1Identity);
    long eventId = event.getId();
    long memberId = Long.parseLong(testuser2Identity.getId()); // member, not invited
    long outsiderId = Long.parseLong(testuser4Identity.getId()); // neither member nor invited

    Event storedEvent = agendaEventService.getEventById(eventId);
    assertEquals(Boolean.TRUE, storedEvent.getOpen());
    assertFalse(agendaEventAttendeeService.isEventAttendee(eventId, memberId));
    assertTrue(agendaEventAttendeeService.canRespondToEvent(storedEvent, memberId));
    assertFalse(agendaEventAttendeeService.canRespondToEvent(storedEvent, outsiderId));
    assertFalse(agendaEventAttendeeService.canRespondToEvent(null, memberId));
    assertFalse(agendaEventAttendeeService.canRespondToEvent(storedEvent, 0));

    // Reading one's answer goes through the same gate: no answer yet, no error
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, agendaEventAttendeeService.getEventResponse(eventId, null, memberId));

    // Answering is becoming an attendee
    agendaEventAttendeeService.sendEventResponse(eventId, memberId, EventAttendeeResponse.ACCEPTED);
    assertTrue(agendaEventAttendeeService.isEventAttendee(eventId, memberId));
    assertEquals(EventAttendeeResponse.ACCEPTED, agendaEventAttendeeService.getEventResponse(eventId, null, memberId));

    // A second answer updates that row, as for an invitee
    agendaEventAttendeeService.sendEventResponse(eventId, memberId, EventAttendeeResponse.TENTATIVE);
    assertEquals(EventAttendeeResponse.TENTATIVE, agendaEventAttendeeService.getEventResponse(eventId, null, memberId));
    assertEquals(1, agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(memberId).size());

    // Opening widens the answer right to whoever can see the event, no further
    try {
      agendaEventAttendeeService.sendEventResponse(eventId, outsiderId, EventAttendeeResponse.ACCEPTED);
      fail("a user who can't access the event can't answer it, open or not");
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventAttendeeService.getEventResponse(eventId, null, outsiderId);
      fail("a user who can't access the event can't read an answer on it either");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertFalse(agendaEventAttendeeService.isEventAttendee(eventId, outsiderId));

    // A non-user identity (the space itself acting as principal) can't answer
    // either, mirroring canAccessEvent: the open half admits users only
    long spaceIdentityId = Long.parseLong(spaceIdentity.getId());
    assertFalse(agendaEventAttendeeService.canRespondToEvent(storedEvent, spaceIdentityId));
    try {
      agendaEventAttendeeService.sendEventResponse(eventId, spaceIdentityId, EventAttendeeResponse.ACCEPTED);
      fail("a space acting as principal can't answer an open event");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertFalse(agendaEventAttendeeService.isEventAttendee(eventId, spaceIdentityId));
  }

  @Test
  public void testLockedEventStillRefusesANonAttendee() throws Exception { // NOSONAR
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberId = Long.parseLong(testuser2Identity.getId());
    long inviteeId = Long.parseLong(testuser5Identity.getId()); // invited, not a member

    Event lockedEvent = createSpaceEvent(getDate().withNano(0), false, false, testuser1Identity, testuser5Identity);
    Event storedEvent = agendaEventService.getEventById(lockedEvent.getId());
    assertEquals(Boolean.FALSE, storedEvent.getOpen());
    assertFalse(agendaEventAttendeeService.canRespondToEvent(storedEvent, memberId));
    try {
      agendaEventAttendeeService.sendEventResponse(lockedEvent.getId(), memberId, EventAttendeeResponse.ACCEPTED);
      fail("a space member who isn't invited can't answer a locked event");
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventAttendeeService.getEventResponse(lockedEvent.getId(), null, memberId);
      fail("a space member who isn't invited can't read an answer on a locked event");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertFalse(agendaEventAttendeeService.isEventAttendee(lockedEvent.getId(), memberId));

    // The invitee's experience is untouched, member of the space or not
    assertTrue(agendaEventAttendeeService.canRespondToEvent(storedEvent, inviteeId));
    agendaEventAttendeeService.sendEventResponse(lockedEvent.getId(), inviteeId, EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(lockedEvent.getId(), null, inviteeId));

    // A personal-calendar event is never open, whatever the payload asked for
    Event personalEvent = newEventInstance(getDate().withNano(0), getDate().withNano(0), true);
    personalEvent.setRecurrence(null);
    personalEvent.setOpen(true);
    personalEvent = createEvent(personalEvent.clone(), creatorId, testuser1Identity);
    Event storedPersonalEvent = agendaEventService.getEventById(personalEvent.getId());
    assertEquals(Boolean.FALSE, storedPersonalEvent.getOpen());
    assertFalse(agendaEventAttendeeService.canRespondToEvent(storedPersonalEvent, memberId));
    try {
      agendaEventAttendeeService.sendEventResponse(personalEvent.getId(), memberId, EventAttendeeResponse.ACCEPTED);
      fail("nobody self-registers on a personal-calendar event");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  @Test
  public void testOpenFlagOfTheSeriesGatesItsOccurrences() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberId = Long.parseLong(testuser2Identity.getId());
    long otherMemberId = Long.parseLong(testuser3Identity.getId());

    Event series = createSpaceEvent(start, true, true, testuser1Identity);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start);
    Event storedOccurrence = agendaEventService.getEventById(occurrence.getId());
    // Since US06 the occurrence inherits the state of its series when it is
    // materialised, and may later carry a state of its own
    assertEquals(Boolean.TRUE, storedOccurrence.getOpen());
    assertTrue(agendaEventAttendeeService.canRespondToEvent(storedOccurrence, memberId));

    // Answering on one occurrence enrols on that occurrence only
    agendaEventAttendeeService.sendEventResponse(occurrence.getId(), memberId, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(occurrence.getId(), null, memberId));
    assertFalse(agendaEventAttendeeService.isEventAttendee(series.getId(), memberId));

    // "This and upcoming" on the series goes through the same door
    ZonedDateTime fromOccurrenceId = start.plusDays(1);
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         fromOccurrenceId,
                                                         memberId,
                                                         EventAttendeeResponse.TENTATIVE);
    assertTrue(agendaEventAttendeeService.isEventAttendee(series.getId(), memberId));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(series.getId(), fromOccurrenceId, memberId));

    // Locking the series locks its occurrences for whoever hasn't answered yet
    agendaEventService.updateEventFields(series.getId(), getFields("open", "false"), true, false, creatorId);
    Event lockedOccurrence = agendaEventService.getEventById(occurrence.getId());
    assertFalse(agendaEventAttendeeService.canRespondToEvent(lockedOccurrence, otherMemberId));
    try {
      agendaEventAttendeeService.sendEventResponse(occurrence.getId(), otherMemberId, EventAttendeeResponse.ACCEPTED);
      fail("an occurrence of a locked series refuses a non-attendee");
    } catch (IllegalAccessException e) {
      // Expected
    }
    // while whoever registered before the lock keeps answering, as an attendee (D4)
    agendaEventAttendeeService.sendEventResponse(occurrence.getId(), memberId, EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(occurrence.getId(), null, memberId));
  }

  @Test
  public void testRemovedParticipantOfAnOpenSeriesMayRegisterAgainOnThatOccurrence() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberId = Long.parseLong(testuser2Identity.getId());

    Event series = createSpaceEvent(start, true, true, testuser1Identity, testuser2Identity);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start);
    assertTrue(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), memberId));

    // The organiser removes the participant from this occurrence only
    removeAttendee(occurrence.getId(), memberId, creatorId);
    assertFalse(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), memberId));
    assertTrue(agendaEventAttendeeService.isEventAttendee(series.getId(), memberId));

    // Neither the opening nor an answer on the series puts them back (D6)
    agendaEventAttendeeService.sendEventResponse(series.getId(), memberId, EventAttendeeResponse.ACCEPTED);
    assertFalse(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), memberId));

    // The removal is not a ban: on an open series they register again by themselves
    Event storedOccurrence = agendaEventService.getEventById(occurrence.getId());
    assertTrue(agendaEventAttendeeService.canRespondToEvent(storedOccurrence, memberId));
    agendaEventAttendeeService.sendEventResponse(occurrence.getId(), memberId, EventAttendeeResponse.TENTATIVE);
    assertTrue(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), memberId));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(occurrence.getId(), null, memberId));

    // On a locked series the removal holds, exactly as today (US05)
    agendaEventService.updateEventFields(series.getId(), getFields("open", "false"), true, false, creatorId);
    removeAttendee(occurrence.getId(), memberId, creatorId);
    assertFalse(agendaEventAttendeeService.canRespondToEvent(agendaEventService.getEventById(occurrence.getId()), memberId));
    try {
      agendaEventAttendeeService.sendEventResponse(occurrence.getId(), memberId, EventAttendeeResponse.ACCEPTED);
      fail("a participant removed from an occurrence of a locked series can't answer on it");
    } catch (IllegalAccessException e) {
      // Expected
    }
    agendaEventAttendeeService.sendEventResponse(series.getId(), memberId, EventAttendeeResponse.DECLINED);
    assertFalse(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), memberId));
  }

  @Test
  public void testSelfRegistrationOnTheSeriesCreatesNoRowOnExistingExceptionalOccurrences() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long memberId = Long.parseLong(testuser2Identity.getId());

    Event series = createSpaceEvent(start, true, true, testuser1Identity);
    Event existingOccurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start);

    agendaEventAttendeeService.sendEventResponse(series.getId(), memberId, EventAttendeeResponse.ACCEPTED);
    assertTrue(agendaEventAttendeeService.isEventAttendee(series.getId(), memberId));
    // Stated consequence (spec, Propagation): an exceptional occurrence that
    // already exists is joined by answering on it, occurrence by occurrence
    assertFalse(agendaEventAttendeeService.isEventAttendee(existingOccurrence.getId(), memberId));
    // whereas one created afterwards copies the series list, self-registrant included
    Event laterOccurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start.plusDays(1));
    assertTrue(agendaEventAttendeeService.isEventAttendee(laterOccurrence.getId(), memberId));
  }

  /**
   * Pins the approved formula on the one population the board sentence "same
   * behaviour for invited participants and for self-registered participants"
   * does not cover: an invitee who is not a member of the space. The attendee
   * half of the gate is per event and the open half needs access to the
   * calendar, so once the organiser removed them from an occurrence of an
   * open series they can't register again on it, and can't open its page
   * either (canAccessEvent applies the same two halves). Recorded for the PO
   * and the Architect as a functional divergence (spec revision 4); this pin
   * flips with their decision, it does not settle it.
   */
  @Test
  public void testRemovedInviteeWithoutSpaceAccessCannotRegisterAgainOnTheOccurrence() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long inviteeId = Long.parseLong(testuser5Identity.getId()); // invited, not a member

    Event series = createSpaceEvent(start, true, true, testuser1Identity, testuser5Identity);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start);
    assertTrue(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), inviteeId));

    removeAttendee(occurrence.getId(), inviteeId, creatorId);
    assertTrue(agendaEventAttendeeService.isEventAttendee(series.getId(), inviteeId));
    Event storedOccurrence = agendaEventService.getEventById(occurrence.getId());
    assertFalse(agendaEventAttendeeService.canRespondToEvent(storedOccurrence, inviteeId));
    assertFalse(agendaEventService.canAccessEvent(storedOccurrence, inviteeId));
    try {
      agendaEventAttendeeService.sendEventResponse(occurrence.getId(), inviteeId, EventAttendeeResponse.ACCEPTED);
      fail("an invitee without access to the space can't register again on an occurrence they were removed from");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // They keep answering on the series and its other occurrences, as an attendee
    agendaEventAttendeeService.sendEventResponse(series.getId(), inviteeId, EventAttendeeResponse.TENTATIVE);
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(series.getId(), null, inviteeId));
    assertFalse(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), inviteeId));
  }

  /**
   * US05, in the board's order: a participant is removed from one date of a
   * locked series, then the organiser opens the event. The opening reaches that
   * date like any other property of the series, but it carries no membership:
   * the row the organiser deleted is not recreated, the participant
   * is still on the series and on every other date, and what they get back is
   * the door any member of the space has on an open date — registering by
   * themselves (D6). One scenario, run through each way an organiser opens a
   * series; here the field patch of the detail page's padlock.
   */
  @Test
  public void testOpeningTheSeriesByAPatchDoesNotPutBackAParticipantRemovedFromOneDate() throws Exception { // NOSONAR
    assertOpeningKeepsTheRemoval((series, creatorId) -> agendaEventService.updateEventFields(series.getId(),
                                                                                              getFields("open", "true"),
                                                                                              true,
                                                                                              false,
                                                                                              creatorId));
  }

  /**
   * The same opening through a full save of the series carrying its own
   * attendee list — what the participants drawer sends on "all occurrences" —
   * which still names the participant, so the propagation sees nobody added
   * and nobody removed.
   */
  @Test
  public void testOpeningTheSeriesByAFullSaveDoesNotPutBackAParticipantRemovedFromOneDate() throws Exception { // NOSONAR
    assertOpeningKeepsTheRemoval((series, creatorId) -> {
      Event seriesToOpen = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
      seriesToOpen.setOpen(true);
      agendaEventService.updateEvent(seriesToOpen,
                                     agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     creatorId);
    });
  }

  /**
   * The narrowest scope of the same gesture: the organiser opens that one date
   * alone (US06). The date is saved with its own attendee list, which does not
   * name the participant, so nothing puts them back; the door they get is the
   * date's own open state, and it opens nothing else — the series and its
   * other dates stay locked, and there they are still the invitee they were.
   */
  @Test
  public void testOpeningOneDateAloneDoesNotPutBackAParticipantRemovedFromIt() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberId = Long.parseLong(testuser2Identity.getId());
    long memberNotInvitedId = Long.parseLong(testuser3Identity.getId());

    Event series = createSpaceEvent(start, false, true, testuser1Identity, testuser2Identity);
    Event removedDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start);
    Event otherDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start.plusDays(1));
    removeAttendee(removedDate.getId(), memberId, creatorId);
    restartTransaction();

    Event dateToOpen = agendaEventService.getEventById(removedDate.getId(), ZoneOffset.UTC, creatorId).clone();
    dateToOpen.setOpen(true);
    agendaEventService.updateEvent(dateToOpen,
                                   agendaEventAttendeeService.getEventAttendees(removedDate.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    restartTransaction();

    Event openedDate = agendaEventService.getEventById(removedDate.getId());
    assertEquals("precondition: that date is open", Boolean.TRUE, openedDate.getOpen());
    assertFalse("opening the date never puts them back on it",
                agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));
    assertTrue("and they may register again on it by themselves",
               agendaEventAttendeeService.canRespondToEvent(openedDate, memberId));

    // The series and its other dates are still locked
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(series.getId()).getOpen());
    assertFalse(agendaEventAttendeeService.canRespondToEvent(agendaEventService.getEventById(series.getId()), memberNotInvitedId));
    assertFalse(agendaEventAttendeeService.canRespondToEvent(agendaEventService.getEventById(otherDate.getId()),
                                                            memberNotInvitedId));
    // where the participant is still the invitee they were
    assertTrue(agendaEventAttendeeService.isEventAttendee(otherDate.getId(), memberId));
    agendaEventAttendeeService.sendEventResponse(series.getId(), memberId, EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(otherDate.getId(), null, memberId));
    assertFalse(agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));
  }

  /**
   * US05, first criterion: an invitee answers an open event exactly as they
   * answered it locked. Their right rests on the attendee half of the gate,
   * which is read first, so it owes nothing to the open half — pinned on an
   * invitee who is not a member of the space, the one population the open half
   * refuses. And "answering an open event is becoming an attendee" is a no-op
   * for someone who already is one: their row is updated, never duplicated.
   */
  @Test
  public void testAnInviteeAnswersAnOpenEventAsTheyAnsweredItLocked() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberInviteeId = Long.parseLong(testuser2Identity.getId());
    long outsiderInviteeId = Long.parseLong(testuser5Identity.getId()); // invited, not a member of the space
    assertFalse("precondition: the open half alone would refuse the outsider",
                Utils.canAccessEventCalendar(identityManager, spaceService, spaceCalendar, outsiderInviteeId));

    Event event = createSpaceEvent(start, false, false, testuser1Identity, testuser2Identity, testuser5Identity);
    int attendeeRows = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees().size();

    // Locked: both answer, as invitees
    agendaEventAttendeeService.sendEventResponse(event.getId(), memberInviteeId, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(event.getId(), outsiderInviteeId, EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(event.getId(), null, memberInviteeId));
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(event.getId(), null, outsiderInviteeId));

    agendaEventService.updateEventFields(event.getId(), getFields("open", "true"), false, false, creatorId);
    restartTransaction();
    Event openEvent = agendaEventService.getEventById(event.getId());
    assertEquals("precondition: the event is open", Boolean.TRUE, openEvent.getOpen());

    // Open: they answer again, as the invitees they still are
    assertTrue(agendaEventAttendeeService.canRespondToEvent(openEvent, outsiderInviteeId));
    agendaEventAttendeeService.sendEventResponse(event.getId(), memberInviteeId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(event.getId(), outsiderInviteeId, EventAttendeeResponse.TENTATIVE);
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(event.getId(), null, memberInviteeId));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(event.getId(), null, outsiderInviteeId));
    assertEquals("an invitee's answer updates their row, it adds none",
                 attendeeRows,
                 agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees().size());
  }

  /** One way an organiser opens a series. */
  private interface SeriesOpener {
    void open(Event series, long creatorId) throws Exception;
  }

  /**
   * The US05 scenario, from the removal on a locked series to the
   * re-registration on the opened date, for one way of opening the series.
   */
  private void assertOpeningKeepsTheRemoval(SeriesOpener opener) throws Exception {
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberId = Long.parseLong(testuser2Identity.getId());

    Event series = createSpaceEvent(start, false, true, testuser1Identity, testuser2Identity);
    Event removedDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start);
    Event otherDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start.plusDays(1));
    removeAttendee(removedDate.getId(), memberId, creatorId);
    restartTransaction();
    assertFalse("precondition: removed from that date",
                agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));
    assertTrue("precondition: still on the series", agendaEventAttendeeService.isEventAttendee(series.getId(), memberId));
    assertTrue("precondition: still on the other date",
               agendaEventAttendeeService.isEventAttendee(otherDate.getId(), memberId));
    assertFalse("precondition: while the series is locked, that date is closed to them",
                agendaEventAttendeeService.canRespondToEvent(agendaEventService.getEventById(removedDate.getId()), memberId));

    opener.open(series, creatorId);
    restartTransaction();

    Event openedRemovedDate = agendaEventService.getEventById(removedDate.getId());
    assertEquals("the opening reached the date they were removed from", Boolean.TRUE, openedRemovedDate.getOpen());
    assertFalse("opening never puts them back", agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));
    assertTrue("they remain a participant of the series", agendaEventAttendeeService.isEventAttendee(series.getId(), memberId));
    assertTrue("and of every other date", agendaEventAttendeeService.isEventAttendee(otherDate.getId(), memberId));

    // Their answer on the series reaches the dates they are on, and only those
    agendaEventAttendeeService.sendEventResponse(series.getId(), memberId, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(otherDate.getId(), null, memberId));
    assertFalse("an answer on the series doesn't put them back either",
                agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));
    // Nor does an answer "from this date on", whose own loop over the stored
    // dates keeps the same guard
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         removedDate.getOccurrence().getId(),
                                                         memberId,
                                                         EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(otherDate.getId(), null, memberId));
    assertFalse("an answer from that date on doesn't put them back either",
                agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));

    // The removal is not a ban: on the open date they register again by themselves
    assertTrue(agendaEventAttendeeService.canRespondToEvent(openedRemovedDate, memberId));
    agendaEventAttendeeService.sendEventResponse(removedDate.getId(), memberId, EventAttendeeResponse.TENTATIVE);
    assertTrue(agendaEventAttendeeService.isEventAttendee(removedDate.getId(), memberId));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(removedDate.getId(), null, memberId));
    assertEquals("registering on one date changes nothing on the others",
                 EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(otherDate.getId(), null, memberId));
  }

  /**
   * An event of the space calendar, created by testuser1. The default
   * recurrence of newEventInstance is daily for three days.
   */
  private Event createSpaceEvent(ZonedDateTime start,
                                 boolean open,
                                 boolean recurrent,
                                 Identity... attendees) throws Exception {
    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(open);
    if (!recurrent) {
      event.setRecurrence(null);
    }
    return createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), attendees);
  }

  /** What the organiser does from the participants drawer of one occurrence. */
  private void removeAttendee(long eventId, long identityId, long modifierId) {
    Event event = agendaEventService.getEventById(eventId);
    List<EventAttendee> remainingAttendees = agendaEventAttendeeService.getEventAttendees(eventId)
                                                                       .getEventAttendees()
                                                                       .stream()
                                                                       .filter(attendee -> attendee.getIdentityId() != identityId)
                                                                       .collect(Collectors.toList());
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  remainingAttendees,
                                                  modifierId,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(eventId, event.getCalendarId(), modifierId));
  }
}
