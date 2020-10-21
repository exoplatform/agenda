package org.exoplatform.agenda.search;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;

public class AgendaIndexingServiceConnectorTest extends BaseAgendaEventTest {

  AgendaEventAttendeeService     attendeeService                = null;

  AgendaIndexingServiceConnector agendaIndexingServiceConnector = null;

  @Override
  public void setUp() throws ObjectNotFoundException {
    super.setUp();

    this.attendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);

    agendaIndexingServiceConnector = new AgendaIndexingServiceConnector(agendaCalendarService,
            agendaEventService,
            attendeeService,
            getParams());
  }

  @Test
  public void testGetAllIds() {
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
  public void testCreate() throws Exception {
    // NOSONAR
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
    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);
    Document document = agendaIndexingServiceConnector.create(String.valueOf(event.getId()));

    assertNotNull(document);
    assertEquals(String.valueOf(event.getId()), document.getId());
    assertEquals(String.valueOf(event.getId()), document.getFields().get("id"));
    assertEquals(String.valueOf(calendar.getOwnerId()), document.getFields().get("ownerId"));
    assertEquals(String.valueOf(calendar.getId()), document.getFields().get("calendarId"));
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
    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);

    Document document = agendaIndexingServiceConnector.update(String.valueOf(event.getId()));
    assertNotNull(document);
    assertEquals(String.valueOf(event.getId()), document.getId());
    assertEquals(String.valueOf(event.getId()), document.getFields().get("id"));
    assertEquals(String.valueOf(calendar.getOwnerId()), document.getFields().get("ownerId"));
    assertEquals(String.valueOf(calendar.getId()), document.getFields().get("calendarId"));
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
