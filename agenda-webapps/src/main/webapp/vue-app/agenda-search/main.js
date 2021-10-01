// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

import './initComponents.js';

export const USER_TIMEZONE_ID = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;

export function formatSearchResult(results) {
  return results || [];
}

export function fetchSearchResult(uri, options) {
  uri = `${uri}&timeZoneId=${USER_TIMEZONE_ID}`;
  return fetch(uri, options);
}