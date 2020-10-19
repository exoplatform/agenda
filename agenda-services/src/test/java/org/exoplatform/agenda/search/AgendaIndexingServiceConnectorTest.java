package org.exoplatform.agenda.search;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.social.core.activity.model.*;
import org.exoplatform.social.core.activity.model.ActivityStream.Type;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.jpa.search.ActivityIndexingServiceConnector;
import org.exoplatform.social.core.jpa.search.ActivitySearchProcessor;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.processor.I18NActivityProcessor;

@RunWith(MockitoJUnitRunner.class)
public class AgendaIndexingServiceConnectorTest extends BaseAgendaEventTest {

  AgendaIndexingServiceConnector agendaIndexingServiceConnector = null;

  @Mock
  AgendaCalendarService          agendaCalendarService;

  @Mock
  AgendaEventService             agendaEventService;

  @Mock
  AgendaEventAttendeeService     attendeeService;

  @Mock
  IdentityManager                identityManager;

  @Test
  public void testGetAllIds() {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
                                                                        identityManager,
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    try {
      agendaIndexingServiceConnector.getAllIds(0, 10);
      fail("getAllIds shouldn't be supported");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
  }

  @Test
  public void testCanReindex() {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
                                                                        identityManager,
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    assertFalse(agendaIndexingServiceConnector.canReindex());
  }

  @Test
  public void testGetType() {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
                                                                        identityManager,
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    assertEquals(AgendaIndexingServiceConnector.TYPE, agendaIndexingServiceConnector.getType());
  }

  @Test
  public void testCreate() throws Exception { // NOSONAR
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
                                                                        identityManager,
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    try {
      agendaIndexingServiceConnector.create(null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaIndexingServiceConnector.create("1");
      fail("IllegalStateException should be thrown");
    } catch (IllegalStateException e) {
      // Expected
    }
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);

    /*
     * Identity streamOwner = new Identity("streamOwner");
     * when(identityManager.getOrCreateIdentity(Type.USER.getProviderId(),
     * "prettyId")).thenReturn(streamOwner);
     */
    when(agendaEventService.getEventById(eq(1))).thenReturn(event);

    Document document = agendaIndexingServiceConnector.create("1");
    assertNotNull(document);
    assertEquals("1", document.getId());
    assertEquals("2", document.getFields().get("parentId"));
    assertEquals("3", document.getFields().get("parentCommentId"));
    assertEquals("type", document.getFields().get("type"));
    assertEquals(ActivityIndexingServiceConnector.TYPE, document.getType());
    assertEquals("posterId", document.getFields().get("posterId"));
    assertEquals("1234", document.getFields().get("postedTime"));
    assertNotNull(document.getLastUpdatedDate());
    assertEquals(4321L, document.getLastUpdatedDate().getTime());
    assertNotNull(document.getPermissions());
    assertEquals(1, document.getPermissions().size());
    assertEquals("streamOwner", document.getPermissions().iterator().next());
  }

  @Test
  public void testUpdate() throws Exception {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
                                                                        identityManager,
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    try {
      agendaIndexingServiceConnector.update(null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaIndexingServiceConnector.update("1");
      fail("IllegalStateException should be thrown");
    } catch (IllegalStateException e) {
      // Expected
    }

    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);

    /*
     * Identity streamOwner = new Identity("streamOwner");
     * when(identityManager.getOrCreateIdentity(Type.USER.getProviderId(),
     * "prettyId")).thenReturn(streamOwner);
     */
    when(agendaEventService.getEventById(eq(1))).thenReturn(event);

    Document document = agendaIndexingServiceConnector.update("1");
    assertNotNull(document);
    assertEquals("1", document.getId());
    assertEquals("2", document.getFields().get("parentId"));
    assertEquals("3", document.getFields().get("parentCommentId"));
    assertEquals("type", document.getFields().get("type"));
    assertEquals(ActivityIndexingServiceConnector.TYPE, document.getType());
    assertEquals("posterId", document.getFields().get("posterId"));
    assertEquals("1234", document.getFields().get("postedTime"));
    assertNotNull(document.getLastUpdatedDate());
    assertEquals(4321L, document.getLastUpdatedDate().getTime());
    assertNotNull(document.getPermissions());
    assertEquals(1, document.getPermissions().size());
    assertEquals("streamOwner", document.getPermissions().iterator().next());
  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    params.addParameter(propertiesParam);
    return params;
  }

}
