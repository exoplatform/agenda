/**
 * Copyright (C) 2026 eXo Platform SAS.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.portlet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.portlet.*;

import org.apache.commons.lang3.StringUtils;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import io.meeds.social.portlet.CMSPortlet;
import io.meeds.social.translation.service.TranslationService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;

public class AgendaTimelinePortlet extends CMSPortlet {

  static final String               SETTINGS_PREFERENCE   = "settings";

  /**
   * Render attribute holding the stored settings once validated and escaped
   * for an inline script. It differs from {@link #SETTINGS_PREFERENCE} since
   * the parent portlet copies every preference as a raw request attribute.
   */
  static final String               TIMELINE_SETTINGS     = "timelineSettings";

  private static final Log          LOG                   = ExoLogger.getLogger(AgendaTimelinePortlet.class);

  private static final String       OBJECT_TYPE           = "agendaTimeline";

  private static final String       HEADER_FIELD_NAME     = "headerTitle";

  private static final String       EMPTY_SETTINGS        = "{}";

  private static final Set<String>  BOOLEAN_SETTINGS      = Set.of("customHeader",
                                                                   "displaySeeMore",
                                                                   "displayPending",
                                                                   "displayAddEvent");

  private static final Map<String, Set<String>> ENUM_SETTINGS = Map.of("agendaSource",
                                                                       Set.of("", "allUsersSpaces", "selectedSpaces"),
                                                                       "agendaFilter",
                                                                       Set.of("", "allEvents", "acceptedEvents"),
                                                                       "displayMode",
                                                                       Set.of("dynamic", "list"));

  private static final Set<String>  ALLOWED_URL_SCHEMES   = Set.of("http", "https", "mailto", "tel");

  private static final Pattern      URL_SCHEME_PATTERN    = Pattern.compile("^([a-z][a-z0-9+.-]*):");

  private static final Pattern      URL_IGNORED_CHARS     = Pattern.compile("[\\s\\p{Cntrl}]");

  private static final Pattern      ITEMS_NUMBER_PATTERN  = Pattern.compile("\\d{1,3}");

  private static final int          MAX_ITEMS_NUMBER      = 100;

  // The default preference of portlet.xml quotes a value with single quotes
  private static final JsonMapper   JSON_MAPPER           = JsonMapper.builder()
                                                                      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                                                                      .build();

  private TranslationService        translationService;

  @Override
  public void init(PortletConfig config) throws PortletException {
    super.init(config);
    this.contentType = OBJECT_TYPE;
  }

  @Override
  public void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
    String name = request.getPreferences().getValue(NAME, null);
    String headerTitle = getTranslationService().getTranslationLabelOrDefault(OBJECT_TYPE,
            name,
            HEADER_FIELD_NAME,
            request.getLocale());
    if (headerTitle != null) {
      request.setAttribute(HEADER_FIELD_NAME, headerTitle);
    }
    super.doView(request, response);
  }

  @Override
  public void processAction(ActionRequest request, ActionResponse response) throws IOException, PortletException {
    PortletPreferences preferences = request.getPreferences();
    if (!canEditCurrentInstance(preferences)) {
      // Only a forged request reaches this path since the settings drawer is
      // displayed with the edit permission only. The portal still answers it
      // with a 200 status, with no state change.
      throw new PortletException("Current user is not authorized to edit this Agenda Timeline instance");
    }
    String settings = normalizeSettings(request.getParameter(SETTINGS_PREFERENCE));
    if (settings == null) {
      throw new PortletException("Agenda Timeline settings must be a JSON object");
    }
    preferences.setValue(SETTINGS_PREFERENCE, settings);
    preferences.store();
    response.setPortletMode(PortletMode.VIEW);
  }

  @Override
  protected void setViewRequestAttributes(String name, RenderRequest request, RenderResponse response) {
    String settings = normalizeSettings(request.getPreferences().getValue(SETTINGS_PREFERENCE, null));
    request.setAttribute(TIMELINE_SETTINGS, toScriptValue(settings == null ? EMPTY_SETTINGS : settings));
  }

  public TranslationService getTranslationService() {
    if (translationService == null) {
      translationService = ExoContainerContext.getService(TranslationService.class);
    }
    return translationService;
  }

  /**
   * Keeps only the known settings with a value of their expected type
   *
   * @param settings settings as posted by the settings drawer or as stored
   * @return the kept settings as a JSON object, or null when the value isn't
   *         a JSON object
   */
  static String normalizeSettings(String settings) {
    if (StringUtils.isBlank(settings)) {
      return null;
    }
    Map<?, ?> values;
    try {
      values = JSON_MAPPER.readValue(settings, Map.class);
    } catch (Exception e) {
      LOG.debug("Agenda Timeline settings aren't a JSON object", e);
      return null;
    }
    if (values == null) {
      return null;
    }
    Map<String, Object> normalizedValues = new LinkedHashMap<>();
    values.forEach((key, value) -> {
      Object normalizedValue = normalizeValue(String.valueOf(key), value);
      if (normalizedValue != null) {
        normalizedValues.put(String.valueOf(key), normalizedValue);
      }
    });
    try {
      return JSON_MAPPER.writeValueAsString(normalizedValues);
    } catch (Exception e) {
      LOG.warn("Unable to serialize Agenda Timeline settings", e);
      return null;
    }
  }

  /**
   * @param json a JSON value
   * @return the JSON value escaped to be printed inside an inline script
   */
  static String toScriptValue(String json) {
    return json.replace("<", "\\u003c")
               .replace(">", "\\u003e")
               .replace("&", "\\u0026")
               .replace("\u2028", "\\u2028")
               .replace("\u2029", "\\u2029");
  }

  private static Object normalizeValue(String key, Object value) {
    if (BOOLEAN_SETTINGS.contains(key)) {
      return value instanceof Boolean ? value : null;
    } else if (ENUM_SETTINGS.containsKey(key)) {
      return value instanceof String && ENUM_SETTINGS.get(key).contains(value) ? value : null;
    } else if ("seeMoreUrl".equals(key)) {
      return value instanceof String url ? normalizeUrl(url) : null;
    } else if ("itemsNumber".equals(key)) {
      return normalizeItemsNumber(value);
    } else if ("selectedSpaces".equals(key)) {
      return value instanceof List<?> spaces ? spaces.stream().filter(Map.class::isInstance).toList() : null;
    } else {
      return null;
    }
  }

  private static String normalizeUrl(String url) {
    String comparedUrl = URL_IGNORED_CHARS.matcher(url).replaceAll("").toLowerCase(Locale.ENGLISH);
    Matcher matcher = URL_SCHEME_PATTERN.matcher(comparedUrl);
    return !matcher.find() || ALLOWED_URL_SCHEMES.contains(matcher.group(1)) ? url : "";
  }

  private static Integer normalizeItemsNumber(Object value) {
    Integer itemsNumber = null;
    if (value instanceof Integer number) {
      itemsNumber = number;
    } else if (value instanceof String number && ITEMS_NUMBER_PATTERN.matcher(number).matches()) {
      itemsNumber = Integer.valueOf(number);
    }
    return itemsNumber != null && itemsNumber >= 0 && itemsNumber <= MAX_ITEMS_NUMBER ? itemsNumber : null;
  }

  private boolean canEditCurrentInstance(PortletPreferences preferences) {
    String name = preferences.getValue(NAME, null);
    return StringUtils.isNotBlank(name) && canEdit(name, getCurrentIdentity());
  }

  private Identity getCurrentIdentity() {
    return ConversationState.getCurrent() == null ? null : ConversationState.getCurrent().getIdentity();
  }

}
