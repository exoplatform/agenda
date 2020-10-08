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
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaEventServiceImpl;
import org.exoplatform.social.core.jpa.search.ActivitySearchProcessor;
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
import org.exoplatform.social.core.activity.filter.ActivitySearchFilter;
import org.exoplatform.social.core.activity.model.*;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.storage.api.ActivityStorage;

public class AgendaSearchConnector {
  private static final Log              LOG                          = ExoLogger.getLogger(AgendaSearchConnector.class);

  private static final String           SEARCH_QUERY_FILE_PATH_PARAM = "query.file.path";

  private final ConfigurationManager    configurationManager;

  private final IdentityManager        identityManager;

  private final AgendaEventServiceImpl agendaEventService;

  private final ElasticSearchingClient client;

  private String                        index;

  private String                        searchType;

  private String                        searchQueryFilePath;

  private String                        searchQuery;

  public AgendaSearchConnector(ConfigurationManager configurationManager,
                               IdentityManager identityManager,
                               AgendaEventServiceImpl agendaEventService,
                               ElasticSearchingClient client,
                               InitParams initParams) {
    this.configurationManager = configurationManager;
    this.identityManager = identityManager;
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

  public List<EventSearchResult> search(Identity viewerIdentity,
                                           AgendaSearchFilter filter,
                                           long offset,
                                           long limit) {
    if (viewerIdentity == null) {
      throw new IllegalArgumentException("Viewer identity is mandatory");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must be positive");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("Limit must be positive");
    }
    if (filter == null) {
      throw new IllegalArgumentException("Filter is mandatory");
    }
    if (StringUtils.isBlank(filter.getTerm())) {
      throw new IllegalArgumentException("Filter term is mandatory");
    }

    Set<Long> streamFeedOwnerIds = Optional.ofNullable(agendaEventService.getCalendarOwnersOfUser(viewerIdentity)).map(HashSet::new).orElse(null);
    String esQuery = buildQueryStatement(streamFeedOwnerIds, filter, offset, limit);
    String jsonResponse = this.client.sendRequest(esQuery, this.index, this.searchType);
    return buildResult(jsonResponse, viewerIdentity, streamFeedOwnerIds);
  }

  private String buildQueryStatement(Set<Long> streamFeedOwnerIds,
                                     AgendaSearchFilter filter,
                                     long offset,
                                     long limit) {

    String term = removeSpecialCharacters(filter.getTerm());
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
                                .replaceAll("@permissions@", StringUtils.join(streamFeedOwnerIds, ","))
                                .replaceAll("@offset@", String.valueOf(offset))
                                .replaceAll("@limit@", String.valueOf(limit));
  }

  @SuppressWarnings("rawtypes")
  private List<EventSearchResult> buildResult(String jsonResponse, Identity viewerIdentity, Set<Long> streamFeedOwnerIds) {
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
        /*Long streamOwner = parseLong(hitSource, "streamOwner");
        if (!streamFeedOwnerIds.contains(streamOwner)) {
          LOG.warn("Activity '{}' is returned in seach result while it's not permitted to user {}. Ignore it.",
                   id,
                   viewerIdentity.getId());
          continue;
        }*/
        String attendees = (String) hitSource.get("attendee");
        //String[] attendeesArray = (String[]) attendees.toArray(new String[0]);
        List<String> attendeesList = Arrays.asList(attendees);
        String startTime = (String) hitSource.get("startTime");
        String endTime = (String) hitSource.get("endTime");
        String location = (String) hitSource.get("location");
        String summary = (String) hitSource.get("summary");
        String description = (String) hitSource.get("description");
        JSONObject hightlightSource = (JSONObject) jsonHitObject.get("highlight");
        List<String> excerpts = new ArrayList<>();
        if (hightlightSource != null) {
        JSONArray bodyExcepts = (JSONArray) hightlightSource.get("body");
          String[] bodyExceptsArray = (String[]) bodyExcepts.toArray(new String[0]);
          excerpts = Arrays.asList(bodyExceptsArray);
        }

          // Event
          eventSearchResult.setId(id);
          if (startTime != null) {
            eventSearchResult.setStart(startTime);
          }
          if (endTime != null) {
            eventSearchResult.setEnd(endTime);
          }
          if (location != null) {
            eventSearchResult.setLocation(location);
          }
          if (!attendees.isEmpty()) {
            //Identity streamOwnerIdentity = identityManager.getIdentity(streamOwner.toString());
            eventSearchResult.setAttendees(attendeesList);
          }
          /*if (isRecurrent != null) {
            Identity posterIdentity = identityManager.getIdentity(posterId.toString());
            activitySearchResult.setPoster(posterIdentity);
          }*/

          eventSearchResult.setRecurrent(isRecurrent);
          eventSearchResult.setExcerpts(excerpts);

          results.add(eventSearchResult);
      } catch (Exception e) {
        LOG.warn("Error processing event search result item, ignore it from results", e);
      }
    }
    return results;
  }
/*
  private void transformActivityToResult(ActivitySearchResult activitySearchResult, Long parentId) {
    ExoSocialActivity activity = activityStorage.getActivity(parentId.toString());

    // Activity
    activitySearchResult.setType(activity.getType());
    activitySearchResult.setId(Long.parseLong(activity.getId()));
    if (activity.getUpdated() != null) {
      activitySearchResult.setLastUpdatedTime(activity.getUpdated().getTime());
    }

    if (activity.getPostedTime() != null) {
      activitySearchResult.setPostedTime(activity.getPostedTime());
    }
    ActivityStream activityStream = activity.getActivityStream();
    if (activityStream != null) {
      String prettyId = activityStream.getPrettyId();
      String providerId = activityStream.getType().getProviderId();

      Identity streamOwnerIdentity = identityManager.getOrCreateIdentity(providerId, prettyId);
      activitySearchResult.setStreamOwner(streamOwnerIdentity);
    }
    if (activity.getPosterId() != null) {
      Identity posterIdentity = identityManager.getIdentity(activity.getPosterId());
      activitySearchResult.setPoster(posterIdentity);
    }
    if (StringUtils.isNotBlank(activity.getTitle())) {
      activitySearchResult.setBody(activity.getTitle());
    } else {
      activitySearchResult.setBody(activity.getBody());
    }
    activitySearchProcessor.formatSearchResult(activitySearchResult);
  }*/

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
