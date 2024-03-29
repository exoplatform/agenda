package org.exoplatform.agenda.search;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.service.AgendaEventServiceImpl;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.*;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.*;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.class)
public class AgendaSearchConnectorTest extends BaseAgendaEventTest {

  private static final String ES_INDEX        = "event_alias";

  public static final String  FAKE_ES_QUERY   =
                                            "{offset: @offset@, limit: @limit@, term1: @term@, term2: @term@, permissions: @permissions@}";

  @Mock
  IdentityManager             identityManager;                                                                                             // NOSONAR

  @Mock
  AgendaEventServiceImpl      agendaEventService;                                                                                          // NOSONAR

  @Mock
  SpaceService                spaceService;                                                                                                // NOSONAR

  @Mock
  ConfigurationManager        configurationManager;

  @Mock
  ElasticSearchingClient      client;

  AgendaSearchConnector       agendaSearchConnector;                                                                                       // NOSONAR

  String                      searchResult    = null;

  boolean                     developingValue = false;

  @Override
  @Before
  public void setUp() throws ObjectNotFoundException {
    super.setUp();
    try {
      searchResult = IOUtil.getStreamContentAsString(getClass().getClassLoader()
                                                               .getResourceAsStream("agenda-search-result.json"));

      Mockito.reset(configurationManager);
      when(configurationManager.getInputStream("FILE_PATH")).thenReturn(new ByteArrayInputStream(FAKE_ES_QUERY.getBytes()));
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving ES Query content", e);
    }
    developingValue = PropertyManager.isDevelopping();
    PropertyManager.setProperty(PropertyManager.DEVELOPING, "false");
    PropertyManager.refresh();

    agendaSearchConnector = new AgendaSearchConnector(configurationManager,
                                                      identityManager,
                                                      spaceService,
                                                      agendaEventStorage,
                                                      client,
                                                      getParams());
  }

  @Override
  @After
  public void tearDown() throws ObjectNotFoundException {
    super.tearDown();
    PropertyManager.setProperty(PropertyManager.DEVELOPING, String.valueOf(developingValue));
    PropertyManager.refresh();
  }

  @Test
  public void testSearchArguments() {

    String term = "searchTerm";
    try {
      agendaSearchConnector.search(-1, ZoneId.of("US/Hawaii"), term, 0, 10);
      fail("Should throw IllegalArgumentException: viewer identity id is mandatory");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    try {
      agendaSearchConnector.search(Long.parseLong(identity.getId()), ZoneId.of("US/Hawaii"), null, 0, 10);
      fail("Should throw IllegalArgumentException: filter is mandatory");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaSearchConnector.search(Long.parseLong(identity.getId()), ZoneId.of("US/Hawaii"), term, -1, 10);
      fail("Should throw IllegalArgumentException: offset should be positive");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaSearchConnector.search(Long.parseLong(identity.getId()), ZoneId.of("US/Hawaii"), term, 0, -1);
      fail("Should throw IllegalArgumentException: limit should be positive");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testSearchNoResult() {
    String term = "searchTerm";
    HashSet<Long> permissions = new HashSet<>(Arrays.asList(1L));
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    when(identity.getRemoteId()).thenReturn("testuser1");
    when(Utils.getIdentityById(identityManager, 1L)).thenReturn(identity);
    String expectedESQuery = FAKE_ES_QUERY.replaceAll("@term@", term)
                                          .replaceAll("@permissions@", StringUtils.join(permissions, ","))
                                          .replaceAll("@offset@", "0")
                                          .replaceAll("@limit@", "10");
    when(client.sendRequest(eq(expectedESQuery), eq(ES_INDEX))).thenReturn("{}");
    when(spaceService.getMemberSpaces(eq("testuser1"))).thenAnswer(new Answer<ListAccess<Space>>() {
      @Override
      public ListAccess<Space> answer(InvocationOnMock invocation) throws Throwable {
        @SuppressWarnings("unchecked")
        ListAccess<Space> userSpaces = mock(ListAccess.class);
        final int spacesCount = 1;
        when(userSpaces.getSize()).thenReturn(spacesCount);
        when(userSpaces.load(anyInt(), anyInt())).thenAnswer(new Answer<Space[]>() {
          @Override
          public Space[] answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            int size = Integer.parseInt(args[1].toString());
            Space[] spaces = new Space[size];
            spaces[0] = new Space();
            String prettyName = "testspace1";
            int spaceIdentityIndex = 1;
            spaces[0].setId(String.valueOf(spaceIdentityIndex));
            spaces[0].setPrettyName(prettyName);
            Identity spaceIdentity = new Identity(String.valueOf(spaceIdentityIndex));
            spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);
            spaceIdentity.setRemoteId(prettyName);
            when(identityManager.getOrCreateIdentity(eq(SpaceIdentityProvider.NAME),
                                                     eq(prettyName))).thenReturn(spaceIdentity);
            return spaces;
          }
        });
        return userSpaces;
      }
    });

    List<EventSearchResult> result = agendaSearchConnector.search(Long.parseLong(identity.getId()),
                                                                  ZoneId.of("US/Hawaii"),
                                                                  term,
                                                                  0,
                                                                  10);
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testSearchWithResult() throws Exception { // NOSONAR
    String term = "searchTerm";
    HashSet<Long> permissions = new HashSet<>(Arrays.asList(1L));
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    when(identity.getRemoteId()).thenReturn("testuser1");
    when(Utils.getIdentityById(identityManager, 1L)).thenReturn(identity);
    String expectedESQuery = FAKE_ES_QUERY.replaceAll("@term@", term)
                                          .replaceAll("@permissions@", StringUtils.join(permissions, ","))
                                          .replaceAll("@offset@", "0")
                                          .replaceAll("@limit@", "10");
    when(client.sendRequest(eq(expectedESQuery), eq(ES_INDEX))).thenReturn(searchResult);
    long startTime = 1602979200000L;
    long endTime = 1603151999000L;
    ZonedDateTime start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime),
                                                  ZoneId.systemDefault());
    ZonedDateTime end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endTime),
                                                ZoneId.systemDefault());

    boolean allDay = true;

    Event event = newEventInstance(start, end, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    lenient().when(agendaEventService.getEventById(eq(1L))).thenReturn(event);
    when(spaceService.getMemberSpaces(eq("testuser1"))).thenAnswer(new Answer<ListAccess<Space>>() {
      @Override
      public ListAccess<Space> answer(InvocationOnMock invocation) throws Throwable {
        @SuppressWarnings("unchecked")
        ListAccess<Space> userSpaces = mock(ListAccess.class);
        final int spacesCount = 1;
        when(userSpaces.getSize()).thenReturn(spacesCount);
        when(userSpaces.load(anyInt(), anyInt())).thenAnswer(new Answer<Space[]>() {
          @Override
          public Space[] answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            int size = Integer.parseInt(args[1].toString());
            Space[] spaces = new Space[size];
            spaces[0] = new Space();
            String prettyName = "testspace1";
            int spaceIdentityIndex = 1;
            spaces[0].setId(String.valueOf(spaceIdentityIndex));
            spaces[0].setPrettyName(prettyName);
            Identity spaceIdentity = new Identity(String.valueOf(spaceIdentityIndex));
            spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);
            spaceIdentity.setRemoteId(prettyName);
            when(identityManager.getOrCreateIdentity(eq(SpaceIdentityProvider.NAME),
                                                     eq(prettyName))).thenReturn(spaceIdentity);
            return spaces;
          }
        });
        return userSpaces;
      }
    });

    List<EventSearchResult> result = agendaSearchConnector.search(Long.parseLong(identity.getId()),
                                                                  ZoneId.of("US/Hawaii"),
                                                                  term,
                                                                  0,
                                                                  10);
    assertNotNull(result);
    assertEquals(1, result.size());

    EventSearchResult eventSearchResult = result.iterator().next();
    assertEquals(1L, eventSearchResult.getId());
    assertTrue(eventSearchResult.getSummary().contains("searchMatchExcerpt"));
    assertTrue(eventSearchResult.getSummary().contains(term));
    assertEquals(event.getDescription(), eventSearchResult.getDescription());
    assertEquals(1L, eventSearchResult.getCalendarId());
    assertEquals(event.getLocation(), eventSearchResult.getLocation());

    ZonedDateTime eventStartDateInMS = Utils.toDateTime(String.valueOf(startTime), ZoneId.of("US/Hawaii"));
    ZonedDateTime eventEndDateInMS = Utils.toDateTime(String.valueOf(endTime), ZoneId.of("US/Hawaii"));
    assertEquals(eventStartDateInMS, eventSearchResult.getStart());
    assertEquals(eventEndDateInMS, eventSearchResult.getEnd());
    assertEquals(1, eventSearchResult.getExcerpts().size());
  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    propertiesParam.setProperty("index", ES_INDEX);

    ValueParam valueParam = new ValueParam();
    valueParam.setName("query.file.path");
    valueParam.setValue("FILE_PATH");

    params.addParameter(propertiesParam);
    params.addParameter(valueParam);
    return params;
  }

}
