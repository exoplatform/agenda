/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
<template>
  <div>
    <div v-if="loading" class="d-flex justify-center py-2">
      <v-progress-circular
        color="primary"
        size="20"
        width="2"
        indeterminate />
    </div>
    <div
      v-else-if="!calendars.length"
      class="agenda-left-panel-empty text-sub-title">
      {{ emptyLabel }}
    </div>
    <v-list
      v-else
      class="pa-0"
      dense>
      <v-list-item
        v-for="calendar in calendars"
        :key="calendar.id"
        class="agenda-calendar-settings px-0">
        <v-list-item-content :title="calendar.name" class="flex-grow-1 pa-0">
          <v-checkbox
            :input-value="isDisplayed(calendar)"
            :color="calendar.color"
            :label="calendar.name"
            class="agenda-calendar-settings-color ms-4"
            dense
            hide-details
            @change="toggle(calendar)" />
        </v-list-item-content>
      </v-list-item>
    </v-list>
  </div>
</template>

<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    calendars: [],
    hiddenIds: [],
    loading: false,
  }),
  computed: {
    /**
     * The connectors that are connected and able to list calendars.
     *
     * `connected` is the runtime state and is what decides. `isSignedIn` is a
     * static property on the CalDAV descriptor, always true whether or not an
     * account is configured, so gating on it alone meant the section kept
     * asking after the user had disconnected — and the request went out with a
     * null username, drawing the browser's own credentials prompt over the
     * agenda.
     *
     * @returns {Array} the connectors worth asking
     */
    connectedConnectors() {
      return (this.connectors || []).filter(connector => connector
          && connector.canListCalendars
          && connector.connected
          && typeof connector.listCalendars === 'function');
    },
    /**
     * What to say when there is nothing to list, which is two different
     * situations and should not read as one. No account connected is an
     * invitation to connect one; an account holding no calendar is a statement
     * of fact about that account.
     *
     * @returns {String} the message for the current empty case
     */
    emptyLabel() {
      return this.connectedConnectors.length
        ? this.$t('agenda.leftPanel.noRemoteCalendar')
        : this.$t('agenda.leftPanel.notConnected');
    },
  },
  watch: {
    /**
     * Reacts to an account being connected or disconnected while the agenda is
     * open, so the list fills or empties without a reload.
     * @returns {void}
     */
    connectedConnectors() {
      this.retrieveCalendars();
    },
  },
  created() {
    this.retrieveCalendars();
  },
  methods: {
    /**
     * Asks every connected connector able to answer for the calendars of the
     * account behind it, and renders them as one list.
     *
     * A connector that does not declare canListCalendars is skipped rather
     * than called, so the ones that predate this contract — Office 365 and
     * Outlook Exchange — are untouched. A connector that fails is logged and
     * dropped: one unreachable account must not empty the whole section.
     *
     * @returns {void}
     */
    retrieveCalendars() {
      const connectors = this.connectedConnectors;
      if (!connectors.length) {
        this.calendars = [];
        return;
      }
      this.loading = true;
      Promise.all(connectors.map(connector =>
        connector.listCalendars()
          // The calendar eXo writes its copies to is left out of the list: it
          // holds nothing but duplicates of events the agenda already shows,
          // so displaying it would double every meeting on the grid.
          .then(calendars => this.$remoteEventConnector.excludeMirrorCalendar(connector, calendars))
          .catch(error => {
            console.error(`cannot list the calendars of ${connector.name}`, error);
            return [];
          })
      )).then(lists => {
        this.calendars = lists.flat();
      }).finally(() => this.loading = false);
    },
    /**
     * Whether a calendar's events are currently shown. Calendars are displayed
     * unless deliberately hidden, so a newly appearing one shows by default
     * rather than staying invisible until noticed.
     *
     * @param {Object} calendar calendar as the connector described it
     * @returns {Boolean} true when its events are shown
     */
    isDisplayed(calendar) {
      return !this.hiddenIds.includes(calendar.id);
    },
    /**
     * Shows or hides one calendar and publishes the resulting hidden set.
     *
     * @param {Object} calendar calendar the user just toggled
     * @returns {void}
     */
    toggle(calendar) {
      this.hiddenIds = this.isDisplayed(calendar)
        ? this.hiddenIds.concat(calendar.id)
        : this.hiddenIds.filter(id => id !== calendar.id);
      this.$root.$emit('agenda-remote-calendars-changed', this.hiddenIds.slice());
    },
  },
};
</script>
