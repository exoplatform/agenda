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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.rest.AgendaEventRest;
import org.exoplatform.agenda.rest.model.EventAttendeeEntity;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.rest.ApplicationContext;
import org.exoplatform.services.rest.impl.ApplicationContextImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;
import org.exoplatform.social.rest.entity.IdentityEntity;

/**
 * An address typed to invite someone resolves to the platform user whose
 * address it is, through the user directory the container runs (eXIP
 * 7.3.0.21, US06): ignoring case, an enabled user only, never an address
 * the platform keeps private.
 */
public class AgendaEventAttendeeUserByEmailTest extends BaseAgendaEventTest {

  private ProfilePropertyService profilePropertyService;

  private long                   emailSettingId;

  private boolean                emailSettingCreated;

  private boolean                storedActive;

  private boolean                storedVisible;

  private boolean                hidden;

  private boolean                disabled;

  /**
   * Displays the email profile property, creating its setting when the
   * container has none.
   */
  @Before
  public void displayTheEmail() throws Exception {
    // The entity build reads the current user through the social
    // EntityBuilder, which requires an authenticated conversation state and a
    // bound JAX-RS application context, as every real REST call has
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(testuser1Identity.getRemoteId())));
    ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
    Mockito.when(applicationContext.getProperties()).thenReturn(new HashMap<>());
    Mockito.when(applicationContext.getBaseUri()).thenReturn(URI.create("http://localhost:8080/portal/rest"));
    ApplicationContextImpl.setCurrent(applicationContext);
    profilePropertyService = container.getComponentInstanceOfType(ProfilePropertyService.class);
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
   * Leaves the email property and the users as they were found.
   */
  @After
  public void restoreTheEmail() throws Exception {
    ApplicationContextImpl.setCurrent(null);
    ConversationState.setCurrent(null);
    if (hidden) {
      profilePropertyService.showPropertySetting(Long.parseLong(testuser2Identity.getId()), emailSettingId);
      hidden = false;
    }
    if (disabled) {
      container.getComponentInstanceOfType(OrganizationService.class).getUserHandler().setEnabled("testuser3", true, true);
      disabled = false;
    }
    if (emailSettingCreated) {
      profilePropertyService.deleteProfilePropertySetting(emailSettingId);
      emailSettingCreated = false;
    } else {
      displayEmail(storedActive, storedVisible);
    }
  }

  /**
   * @return the identity the lookups are made for: an internal user
   */
  private long callerId() {
    return Long.parseLong(testuser1Identity.getId());
  }

  @Test
  public void anAddressResolvesToItsUserIgnoringCase() {
    String email = emailOf(testuser2Identity);

    Identity user = agendaEventAttendeeService.getUserIdentityByEmail(email, callerId());
    assertEquals(testuser2Identity.getId(), user == null ? null : user.getId());

    Identity typedInCapitals = agendaEventAttendeeService.getUserIdentityByEmail("  " + email.toUpperCase() + " ", callerId());
    assertEquals(testuser2Identity.getId(), typedInCapitals == null ? null : typedInCapitals.getId());
  }

  @Test
  public void anAddressOfNoUserResolvesToNobody() {
    assertNull(agendaEventAttendeeService.getUserIdentityByEmail("nobody.here@outside.example.org", callerId()));
    assertNull(agendaEventAttendeeService.getUserIdentityByEmail(" ", callerId()));
    assertNull(agendaEventAttendeeService.getUserIdentityByEmail(null, callerId()));
  }

  /**
   * Only the address itself resolves: a beginning of an address does not,
   * though the directory of this container finds an address by a part of it.
   */
  @Test
  public void theBeginningOfAnAddressResolvesToNobody() {
    String email = emailOf(testuser2Identity);

    assertNull(agendaEventAttendeeService.getUserIdentityByEmail(email.substring(0, email.length() - 1), callerId()));
  }

  /**
   * A star would turn the lookup into a pattern: one that matches a single
   * user's address does not resolve to that user, which is not the address
   * typed.
   */
  @Test
  public void aPatternResolvesToNobody() {
    String email = emailOf(testuser2Identity);

    assertNull(agendaEventAttendeeService.getUserIdentityByEmail("*" + email.substring(1), callerId()));
  }

  @Test
  public void anAddressThePlatformKeepsPrivateResolvesToNobody() {
    String email = emailOf(testuser2Identity);

    displayEmail(true, false);
    assertNull("the platform displays no e-mail address", agendaEventAttendeeService.getUserIdentityByEmail(email, callerId()));
    displayEmail(false, true);
    assertNull("the e-mail property is not active", agendaEventAttendeeService.getUserIdentityByEmail(email, callerId()));
    displayEmail(true, true);

    profilePropertyService.hidePropertySetting(Long.parseLong(testuser2Identity.getId()), emailSettingId);
    hidden = true;
    assertNull("the user hid their e-mail address", agendaEventAttendeeService.getUserIdentityByEmail(email, callerId()));
  }

  @Test
  public void anAddressOfADisabledUserResolvesToNobody() throws Exception {
    String email = emailOf(testuser3Identity);
    Identity user = agendaEventAttendeeService.getUserIdentityByEmail(email, callerId());
    assertEquals(testuser3Identity.getId(), user == null ? null : user.getId());

    container.getComponentInstanceOfType(OrganizationService.class).getUserHandler().setEnabled("testuser3", false, true);
    disabled = true;

    assertNull(agendaEventAttendeeService.getUserIdentityByEmail(email, callerId()));
  }

  /**
   * A full save of an open event carrying an address typed for a platform
   * user invites that user: an open event refuses a guest from outside the
   * platform, so the save would be refused had the address stayed a guest.
   */
  @Test
  public void aFullSaveOfAnOpenEventInvitesAnAddressTypedAsItsUser() throws Exception {
    long eventId = createEvent(openSpaceEvent(), id(testuser1Identity)).getId();
    EventEntity toSave = entityOf(eventId);
    List<EventAttendeeEntity> attendees = toSave.getAttendees() == null ? new ArrayList<>()
                                                                        : new ArrayList<>(toSave.getAttendees());
    attendees.add(typed(emailOf(testuser4Identity)));
    toSave.setAttendees(attendees);

    Response response = eventRest().updateEvent(toSave, "UTC", false);

    assertEquals(200, response.getStatus());
    assertTrue(agendaEventAttendeeService.isEventAttendee(eventId, id(testuser4Identity)));
  }

  /**
   * An open event created with an address typed for a platform user invites
   * that user.
   */
  @Test
  public void anOpenEventCreatedWithAnAddressTypedInvitesItsUser() throws Exception {
    EventEntity toCreate = entityOf(createEvent(openSpaceEvent(), id(testuser1Identity)).getId());
    toCreate.setId(0);
    toCreate.setAttendees(new ArrayList<>(List.of(typed(emailOf(testuser4Identity)))));

    Response response = eventRest().createEvent("UTC", false, toCreate);

    assertEquals(200, response.getStatus());
    long createdId = ((EventEntity) response.getEntity()).getId();
    assertTrue(agendaEventAttendeeService.isEventAttendee(createdId, id(testuser4Identity)));
  }

  private AgendaEventRest eventRest() {
    return new AgendaEventRest(identityManager,
                               Mockito.mock(UserPortalConfigService.class),
                               agendaCalendarService,
                               agendaEventService,
                               agendaEventConferenceService,
                               agendaRemoteEventService,
                               agendaEventDatePollService,
                               agendaEventReminderService,
                               agendaEventAttendeeService,
                               Mockito.mock(PortalContainer.class));
  }

  /**
   * An event of no series in the space's calendar, starting in sixty days,
   * open: not created yet.
   */
  private Event openSpaceEvent() {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    event.setOpen(true);
    return event;
  }

  /**
   * The entity of an event as the form reads it before saving it.
   */
  private EventEntity entityOf(long eventId) {
    return RestUtils.getEventEntity(identityManager,
                                    agendaCalendarService,
                                    agendaEventService,
                                    agendaRemoteEventService,
                                    agendaEventDatePollService,
                                    agendaEventReminderService,
                                    agendaEventConferenceService,
                                    agendaEventAttendeeService,
                                    agendaEventService.getEventById(eventId),
                                    null,
                                    ZoneOffset.UTC,
                                    List.of("all"));
  }

  /**
   * A participant as the drawer adds an address typed: in lower case, its
   * address for an id.
   */
  private static EventAttendeeEntity typed(String email) {
    IdentityEntity identity = new IdentityEntity();
    identity.setId(email.toLowerCase());
    identity.setProviderId(AgendaGuestUserIdentityProvider.NAME);
    identity.setRemoteId(email.toLowerCase());
    return new EventAttendeeEntity(0, identity, null);
  }

  private static long id(Identity identity) {
    return Long.parseLong(identity.getId());
  }

  private String emailOf(Identity identity) {
    String email = identityManager.getIdentity(identity.getId()).getProfile().getEmail();
    assertTrue("the test user has an e-mail address", StringUtils.isNotBlank(email));
    return email;
  }

  private void displayEmail(boolean active, boolean visible) {
    ProfilePropertySetting setting = profilePropertyService.getProfileSettingById(emailSettingId);
    setting.setActive(active);
    setting.setVisible(visible);
    profilePropertyService.updatePropertySetting(setting);
  }

}
