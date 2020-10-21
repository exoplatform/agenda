package org.exoplatform.agenda.search;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
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
    long calendarId = 13;
    Calendar calendar = new Calendar();
    calendar.setId(calendarId);
    calendar.setOwnerId(1L);
    when(agendaCalendarService.getCalendarById(calendarId)).thenReturn(calendar);
    when(agendaEventService.getEventById(eq(eventId))).thenReturn(event);
    Document document = agendaIndexingServiceConnector.create("1");

    assertNotNull(document);
    assertEquals("1", document.getId());
    assertEquals("2", document.getFields().get("id"));
    assertEquals("9", document.getFields().get("parentId"));
    assertEquals("1", document.getFields().get("ownerId"));
    assertEquals(String.valueOf(calendarId), document.getFields().get("calendarId"));
    assertEquals(event.getSummary(), document.getFields().get("summary"));
    assertEquals(event.getDescription(), document.getFields().get("description"));
    assertEquals(event.getLocation(), document.getFields().get("location"));
    long eventStartTimeInMS = AgendaDateUtils.toDate(event.getStart()).getTime();
    long eventEndTimeInMS = AgendaDateUtils.toDate(event.getEnd()).getTime();
    assertEquals(Long.toString(eventStartTimeInMS), document.getFields().get("startTime"));
    assertEquals(Long.toString(eventEndTimeInMS), document.getFields().get("endTime"));
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
    long calendarId = 15;
    Calendar calendar = new Calendar();
    calendar.setId(calendarId);
    calendar.setOwnerId(1L);
    when(agendaCalendarService.getCalendarById(calendarId)).thenReturn(calendar);
    when(agendaEventService.getEventById(eq(eventId))).thenReturn(event);

    Document document = agendaIndexingServiceConnector.update("3");
    assertNotNull(document);
    assertEquals("3", document.getId());
    assertEquals("3", document.getFields().get("id"));
    assertEquals("1", document.getFields().get("ownerId"));
    assertEquals(String.valueOf(calendarId), document.getFields().get("calendarId"));
    assertEquals(event.getSummary(), document.getFields().get("summary"));
    assertEquals(event.getDescription(), document.getFields().get("description"));
    assertEquals(event.getLocation(), document.getFields().get("location"));
    long eventStartTimeInMS = AgendaDateUtils.toDate(event.getStart()).getTime();
    long eventEndTimeInMS = AgendaDateUtils.toDate(event.getEnd()).getTime();
    assertEquals(Long.toString(eventStartTimeInMS), document.getFields().get("startTime"));
    assertEquals(Long.toString(eventEndTimeInMS), document.getFields().get("endTime"));
  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    params.addParameter(propertiesParam);
    return params;
  }

}
