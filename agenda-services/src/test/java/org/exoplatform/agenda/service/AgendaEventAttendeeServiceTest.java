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

import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.EventInvitationExpiredException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
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

}
