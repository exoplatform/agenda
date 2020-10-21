package org.exoplatform.agenda.search;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
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

  @Test
  public void testGetAllIds() {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
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
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    assertFalse(agendaIndexingServiceConnector.canReindex());
  }

  @Test
  public void testGetType() {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
                                                                        agendaEventService,
                                                                        attendeeService,
                                                                        getParams());
    assertEquals(AgendaIndexingServiceConnector.TYPE, agendaIndexingServiceConnector.getType());
  }

  @Test
  public void testCreate() throws Exception { // NOSONAR
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
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
    long eventId = 1;
    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);
    event.setParentId(9L);

    when(agendaEventService.getEventById(eq(eventId))).thenReturn(event);
    Document document = agendaIndexingServiceConnector.create("1");

    assertNotNull(document);
    assertEquals("1", document.getId());
    assertEquals("2", document.getFields().get("id"));
    assertEquals("9", document.getFields().get("parentId"));
    assertEquals(event.getSummary(), document.getFields().get("summary"));
    assertEquals(event.getDescription(), document.getFields().get("description"));
    assertEquals(event.getLocation(), document.getFields().get("location"));
    long eventStartTimeInMS = AgendaDateUtils.toDate(event.getStart()).getTime();
    long eventEndTimeInMS = AgendaDateUtils.toDate(event.getEnd()).getTime();
    assertEquals(Long.toString(eventStartTimeInMS), document.getFields().get("startTime"));
    assertEquals(Long.toString(eventEndTimeInMS), document.getFields().get("endTime"));
    assertEquals("false", document.getFields().get("isExceptional"));
    assertEquals("true", document.getFields().get("isRecurrent"));
  }

  @Test
  public void testUpdate() throws Exception {
    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
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
    long eventId = 3;
    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);

    when(agendaEventService.getEventById(eq(eventId))).thenReturn(event);

    Document document = agendaIndexingServiceConnector.update("3");
    assertNotNull(document);
    assertEquals("3", document.getId());
    assertEquals("3", document.getFields().get("id"));
    assertEquals(event.getSummary(), document.getFields().get("summary"));
    assertEquals(event.getDescription(), document.getFields().get("description"));
    assertEquals(event.getLocation(), document.getFields().get("location"));
    long eventStartTimeInMS = AgendaDateUtils.toDate(event.getStart()).getTime();
    long eventEndTimeInMS = AgendaDateUtils.toDate(event.getEnd()).getTime();
    assertEquals(Long.toString(eventStartTimeInMS), document.getFields().get("startTime"));
    assertEquals(Long.toString(eventEndTimeInMS), document.getFields().get("endTime"));
    assertEquals("false", document.getFields().get("isExceptional"));
    assertEquals("true", document.getFields().get("isRecurrent"));
  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    params.addParameter(propertiesParam);
    return params;
  }

}
