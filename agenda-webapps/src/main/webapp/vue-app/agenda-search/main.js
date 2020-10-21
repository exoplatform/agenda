import './initComponents.js';

const TIME_ZONE_OFFSET_SECONDS = (eXo.env.portal.timezoneDSTSavings + eXo.env.portal.timezoneOffset) / 1000;

export function formatSearchResult(results) {
  return results || [];
}

export function fetchSearchResult(uri, options) {
  uri = `${uri}&timeZoneOffset=${TIME_ZONE_OFFSET_SECONDS}`;
  return fetch(uri, options);
}