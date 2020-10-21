package org.exoplatform.agenda.search;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.service.AgendaEventServiceImpl;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.*;
import org.exoplatform.social.core.activity.filter.ActivitySearchFilter;
import org.exoplatform.social.core.activity.model.*;
import org.exoplatform.social.core.activity.model.ActivityStream.Type;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.jpa.search.ActivitySearchConnector;
import org.exoplatform.social.core.jpa.search.ActivitySearchProcessor;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.storage.api.ActivityStorage;

@RunWith(MockitoJUnitRunner.class)
public class AgendaSearchConnectorTest extends BaseAgendaEventTest {

  private static final String ES_TYPE          = "event";

  private static final String ES_INDEX         = "event_alias";

  public static final String  FAKE_ES_QUERY    =
                                            "{offset: @offset@, limit: @limit@, term1: @term@, term2: @term@, permissions: @permissions@}";


  @Mock
  IdentityManager             identityManager;

  @Mock
  AgendaEventServiceImpl      agendaEventService;

  @Mock
  ConfigurationManager        configurationManager;

  @Mock
  ElasticSearchingClient      client;

  String                      searchResult     = null;

  boolean                     developingValue = false;

  @Before
  public void setUp() throws ObjectNotFoundException, IOException {
    super.setUp();
    searchResult = IOUtil.getStreamContentAsString(getClass().getClassLoader()
                                                             .getResourceAsStream("agenda-search-result.json"));

    try {
      Mockito.reset(configurationManager);
      when(configurationManager.getInputStream("FILE_PATH")).thenReturn(new ByteArrayInputStream(FAKE_ES_QUERY.getBytes()));
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving ES Query content", e);
    }
    developingValue = PropertyManager.isDevelopping();
    PropertyManager.setProperty(PropertyManager.DEVELOPING, "false");
    PropertyManager.refresh();
  }

  @After
  public void tearDown() throws ObjectNotFoundException {
    super.tearDown();
    PropertyManager.setProperty(PropertyManager.DEVELOPING, String.valueOf(developingValue));
    PropertyManager.refresh();
  }

  @Test
  public void testSearchArguments() {
    AgendaSearchConnector agendaSearchConnector = new AgendaSearchConnector(configurationManager,
                                                                            identityManager,
                                                                            agendaEventService,
                                                                            client,
                                                                            getParams());

    String term = "searchTerm";
    try {
      agendaSearchConnector.search(null, term, 0, 10);
      fail("Should throw IllegalArgumentException: viewer identity is mandatory");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    try {
      agendaSearchConnector.search(identity, null, 0, 10);
      fail("Should throw IllegalArgumentException: filter is mandatory");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaSearchConnector.search(identity, term, -1, 10);
      fail("Should throw IllegalArgumentException: offset should be positive");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaSearchConnector.search(identity, term, 0, -1);
      fail("Should throw IllegalArgumentException: limit should be positive");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testSearchNoResult() {
    AgendaSearchConnector agendaSearchConnector = new AgendaSearchConnector(configurationManager,
                                                                            identityManager,
                                                                            agendaEventService,
                                                                            client,
                                                                            getParams());

    String term = "searchTerm";
    HashSet<Long> permissions = new HashSet<>(Arrays.asList(1L));
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    String expectedESQuery = FAKE_ES_QUERY.replaceAll("@term@", term)
                                          .replaceAll("@permissions@", StringUtils.join(permissions, ","))
                                          .replaceAll("@offset@", "0")
                                          .replaceAll("@limit@", "10");
    when(client.sendRequest(eq(expectedESQuery), eq(ES_INDEX), eq(ES_TYPE))).thenReturn("{}");

    List<EventSearchResult> result = agendaSearchConnector.search(identity, term, 0, 10);
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testSearchWithResult() throws Exception {
    AgendaSearchConnector agendaSearchConnector = new AgendaSearchConnector(configurationManager,
                                                                            identityManager,
                                                                            agendaEventService,
                                                                            client,
                                                                            getParams());

    String term = "searchTerm";
    HashSet<Long> permissions = new HashSet<>(Arrays.asList(1L));
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    //when(activityStorage.getStreamFeedOwnerIds(eq(identity))).thenReturn(permissions);
    String expectedESQuery = FAKE_ES_QUERY.replaceAll("@term@", term)
                                          .replaceAll("@permissions@", StringUtils.join(permissions, ","))
                                          .replaceAll("@offset@", "0")
                                          .replaceAll("@limit@", "10");
    when(client.sendRequest(eq(expectedESQuery), eq(ES_INDEX), eq(ES_TYPE))).thenReturn(searchResult);
    long startTime = 1603065600000L;
    long endTime = 1603151999000L;
    ZonedDateTime start =  ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime),
            ZoneId.systemDefault());
    ZonedDateTime end =  ZonedDateTime.ofInstant(Instant.ofEpochMilli(endTime),
            ZoneId.systemDefault());

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, end, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);
    when(agendaEventService.getEventById(eq(1L))).thenReturn(event);
    List<EventSearchResult> result = agendaSearchConnector.search(identity, term, 0, 10);
    assertNotNull(result);
    assertEquals(1, result.size());

    EventSearchResult eventSearchResult = result.iterator().next();
    assertEquals(1L, eventSearchResult.getId());
    assertTrue(eventSearchResult.getSummary().contains("searchMatchExcerpt"));
    assertTrue(eventSearchResult.getSummary().contains(term));
    assertEquals(event.getDescription(), eventSearchResult.getDescription());
    assertEquals(1L, eventSearchResult.getOwnerId());
    assertEquals(event.getLocation(), eventSearchResult.getLocation());
    LocalDateTime eventLocalStartTime = LocalDateTime.ofInstant(event.getStart().toInstant(), ZoneId.systemDefault());
    LocalDateTime eventLocalEndTime = LocalDateTime.ofInstant(event.getEnd().toInstant(), ZoneId.systemDefault());
    assertEquals(eventLocalStartTime.toString(), eventSearchResult.getStart());
    assertEquals(eventLocalEndTime.toString(), eventSearchResult.getEnd());
    assertEquals(1, eventSearchResult.getExcerpts().size());
  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    propertiesParam.setProperty("index", ES_INDEX);
    propertiesParam.setProperty("searchType", ES_TYPE);

    ValueParam valueParam = new ValueParam();
    valueParam.setName("query.file.path");
    valueParam.setValue("FILE_PATH");

    params.addParameter(propertiesParam);
    params.addParameter(valueParam);
    return params;
  }

}
