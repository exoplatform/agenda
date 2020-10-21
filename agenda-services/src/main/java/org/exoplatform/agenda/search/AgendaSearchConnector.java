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
package org.exoplatform.agenda.search;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.service.AgendaEventServiceImpl;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.exoplatform.commons.search.es.ElasticSearchException;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class AgendaSearchConnector {
  private static final Log             LOG                          = ExoLogger.getLogger(AgendaSearchConnector.class);

  private static final String          SEARCH_QUERY_FILE_PATH_PARAM = "query.file.path";

  private final ConfigurationManager   configurationManager;

  private final IdentityManager        identityManager;

  private final SpaceService           spaceService;

  private final ElasticSearchingClient client;

  private String                       index;

  private String                       searchType;

  private String                       searchQueryFilePath;

  private String                       searchQuery;

  public AgendaSearchConnector(ConfigurationManager configurationManager,
                               IdentityManager identityManager,
                               SpaceService spaceService,
                               AgendaEventServiceImpl agendaEventService,
                               ElasticSearchingClient client,
                               InitParams initParams) {
    this.configurationManager = configurationManager;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.agendaEventService = agendaEventService;
    this.client = client;

    PropertiesParam param = initParams.getPropertiesParam("constructor.params");
    this.index = param.getProperty("index");
    this.searchType = param.getProperty("searchType");
    if (initParams.containsKey(SEARCH_QUERY_FILE_PATH_PARAM)) {
      searchQueryFilePath = initParams.getValueParam(SEARCH_QUERY_FILE_PATH_PARAM).getValue();
      try {
        retrieveSearchQuery();
      } catch (Exception e) {
        LOG.error("Can't read elasticsearch search query from path {}", searchQueryFilePath, e);
      }
    }
  }

  public List<EventSearchResult> search(Identity viewerIdentity, String term, long offset, long limit) {
    if (viewerIdentity == null) {
      throw new IllegalArgumentException("Viewer identity is mandatory");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must be positive");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("Limit must be positive");
    }
    if (StringUtils.isBlank(term)) {
      throw new IllegalArgumentException("Filter term is mandatory");
    }

    Set<Long> calendarOwnersOfUser = Optional.ofNullable(Utils.getCalendarOwnersOfUser(spaceService, identityManager, viewerIdentity))
                                             .map(HashSet::new)
                                             .orElse(null);

    calendarOwnersOfUser.add(Long.valueOf(viewerIdentity.getId()));
    String esQuery = buildQueryStatement(calendarOwnersOfUser, term, offset, limit);
    String jsonResponse = this.client.sendRequest(esQuery, this.index, this.searchType);
    return buildResult(jsonResponse, viewerIdentity, calendarOwnersOfUser);
  }

  private String buildQueryStatement(Set<Long> calendarOwnersOfUser, String term, long offset, long limit) {

    term = removeSpecialCharacters(term);
    List<String> termsQuery = Arrays.stream(term.split(" ")).filter(StringUtils::isNotBlank).map(word -> {
      word = word.trim();
      if (word.length() > 5) {
        word = word + "~1";
      }
      return word;
    }).collect(Collectors.toList());
    String termQuery = StringUtils.join(termsQuery, " AND ");
    return retrieveSearchQuery().replaceAll("@term@", term)
                                .replaceAll("@term_query@", termQuery)
                                .replaceAll("@permissions@", StringUtils.join(calendarOwnersOfUser, ","))
                                .replaceAll("@offset@", String.valueOf(offset))
                                .replaceAll("@limit@", String.valueOf(limit));
  }

  @SuppressWarnings("rawtypes")
  private List<EventSearchResult> buildResult(String jsonResponse, Identity viewerIdentity, Set<Long> calendarOwnersOfUser) {
    LOG.debug("Search Query response from ES : {} ", jsonResponse);

    List<EventSearchResult> results = new ArrayList<>();
    JSONParser parser = new JSONParser();

    Map json = null;
    try {
      json = (Map) parser.parse(jsonResponse);
    } catch (ParseException e) {
      throw new ElasticSearchException("Unable to parse JSON response", e);
    }

    JSONObject jsonResult = (JSONObject) json.get("hits");
    if (jsonResult == null) {
      return results;
    }

    //
    JSONArray jsonHits = (JSONArray) jsonResult.get("hits");
    for (Object jsonHit : jsonHits) {
      try {
        EventSearchResult eventSearchResult = new EventSearchResult();

        JSONObject jsonHitObject = (JSONObject) jsonHit;
        JSONObject hitSource = (JSONObject) jsonHitObject.get("_source");
        Long id = parseLong(hitSource, "id");
        Long ownerId = parseLong(hitSource, "ownerId");
        Long parentId = parseLong(hitSource, "parentId");
        Boolean isRecurrent = Boolean.parseBoolean((String) hitSource.get("isRecurrent"));
        Boolean isExceptional = Boolean.parseBoolean((String) hitSource.get("isExceptional"));

        if (!calendarOwnersOfUser.contains(ownerId)) { LOG.
        warn("Event '{}' is returned in seach result while it's not permitted to user {}. Ignore it."
        , id, viewerIdentity.getId());
        continue; }
        
        String attendees = (String) hitSource.get("attendee");
        List<String> attendeesList = Arrays.asList(attendees);
        String startTime = (String) hitSource.get("startTime");
        String endTime = (String) hitSource.get("endTime");
        String location = (String) hitSource.get("location");
        String summary = (String) hitSource.get("summary");
        String description = (String) hitSource.get("description");
        JSONObject highlightSource = (JSONObject) jsonHitObject.get("highlight");
        List<String> excerpts = new ArrayList<>();
        if (highlightSource != null) {
          if (highlightSource.get("summary") != null) {
            summary = ((JSONArray) highlightSource.get("summary")).get(0).toString();
            highlightSource.remove("summary");
          }
          for (Object key : highlightSource.keySet()) {
            excerpts.add(((JSONArray) highlightSource.get(key)).get(0).toString());
          }
        }

        // Event
        if (id != null) {
          eventSearchResult.setId(id);
        }
        if (ownerId != null) {
          eventSearchResult.setOwnerId(ownerId);
        }
        if (startTime != null) {
          eventSearchResult.setStart(startTime);
        }
        if (endTime != null) {eventSearchResult.setEnd(endTime);
        }
        if (location != null) {
          eventSearchResult.setLocation(location);
        }
        if (summary != null) {
          eventSearchResult.setSummary(summary);
        }
        if (description != null) {
          eventSearchResult.setDescription(description);
        }
        eventSearchResult.setIsRecurrent(isRecurrent);
        eventSearchResult.setExcerpts(excerpts);
        eventSearchResult.setAttendees(attendeesList);
        if (parentId != null && !isExceptional) {
          continue;
        }
        results.add(eventSearchResult);
      } catch (Exception e) {
        LOG.warn("Error processing event search result item, ignore it from results", e);
      }
    }
    return results;
  }

  private Long parseLong(JSONObject hitSource, String key) {
    String value = (String) hitSource.get(key);
    return StringUtils.isBlank(value) ? null : Long.parseLong(value);
  }

  private String retrieveSearchQuery() {
    if (StringUtils.isBlank(this.searchQuery) || PropertyManager.isDevelopping()) {
      try {
        InputStream queryFileIS = this.configurationManager.getInputStream(searchQueryFilePath);
        this.searchQuery = IOUtil.getStreamContentAsString(queryFileIS);
      } catch (Exception e) {
        throw new IllegalStateException("Error retrieving search query from file: " + searchQueryFilePath, e);
      }
    }
    return this.searchQuery;
  }

  private String removeSpecialCharacters(String string) {
    string = Normalizer.normalize(string, Normalizer.Form.NFD);
    string = string.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replaceAll("'", " ");
    return string;
  }
}
