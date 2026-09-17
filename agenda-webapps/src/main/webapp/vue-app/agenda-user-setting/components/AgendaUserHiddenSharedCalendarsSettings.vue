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
 * along with this program. If not, see gnu.org/licenses.
 */
<template>
  <!--
    The calendars colleagues shared with the user in eXo and that the user hid
    from their agenda (EXO-90357): where a hidden share is shown again. The
    row draws nothing while nothing is hidden — the settings page must not
    grow a row for a state that has not happened — and lists each hidden
    calendar with its owner and one action.
  -->
  <v-list-item v-if="hiddenCalendars.length" class="agenda-hidden-shared-calendars">
    <v-list-item-content>
      <v-list-item-title class="text-color">
        {{ $t('agenda.calendarShare.settings.hiddenTitle') }}
      </v-list-item-title>
      <v-list-item-subtitle>
        {{ $t('agenda.calendarShare.settings.hiddenCount', {0: hiddenCalendars.length}) }}
      </v-list-item-subtitle>
      <v-list class="pa-0" dense>
        <v-list-item
          v-for="calendar in hiddenCalendars"
          :key="calendar.calendarId"
          class="px-0 agenda-hidden-shared-calendar">
          <v-list-item-content>
            <v-list-item-title>{{ calendar.name }}</v-list-item-title>
            <v-list-item-subtitle>
              {{ $t('agenda.leftPanel.sharedBy', {0: calendar.ownerDisplayName || calendar.ownerUsername}) }}
            </v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action>
            <v-btn
              :disabled="showingIds.includes(calendar.calendarId)"
              class="primary--text text-none px-1 agenda-hidden-shared-calendar-show"
              small
              text
              @click="show(calendar)">
              {{ $t('agenda.calendarShare.settings.showAgain') }}
            </v-btn>
          </v-list-item-action>
        </v-list-item>
      </v-list>
    </v-list-item-content>
  </v-list-item>
</template>

<script>
export default {
  data: () => ({
    calendars: [],
    showingIds: [],
  }),
  computed: {
    /**
     * @returns {Array} the shared calendars the user hid
     */
    hiddenCalendars() {
      return this.calendars.filter(calendar => calendar.hidden);
    },
  },
  created() {
    this.$root.$on('agenda-settings-refresh', this.retrieve);
    // Hidden from the agenda, which is another Vue app: the document is the
    // only signal that crosses that boundary
    document.addEventListener('agenda-refresh-shared-calendars', this.retrieve);
    this.retrieve();
  },
  beforeDestroy() {
    this.$root.$off('agenda-settings-refresh', this.retrieve);
    document.removeEventListener('agenda-refresh-shared-calendars', this.retrieve);
  },
  methods: {
    /**
     * Reads the calendars shared with the user, hidden ones said so. A
     * failure leaves the row absent: the page must not be held up by it.
     *
     * @returns {Promise} resolved once read or given up on
     */
    retrieve() {
      return this.$calendarShareService.getSharedWithMe()
        .then(calendars => this.calendars = calendars || [])
        .catch(error => {
          console.error('cannot read the calendars shared with me', error);
          this.calendars = [];
        });
    },
    /**
     * Shows a hidden calendar again: the server records it, the row goes, and
     * the agenda — another Vue app — is told to list the calendar again.
     *
     * @param {Object} calendar the hidden calendar
     * @returns {Promise} resolved once shown; never rejects
     */
    show(calendar) {
      this.showingIds = this.showingIds.concat(calendar.calendarId);
      return this.$calendarShareService.setHidden(calendar.calendarId, false)
        .then(() => {
          this.calendars = this.calendars.map(row => (row.calendarId === calendar.calendarId ? {...row, hidden: false} : row));
          this.$root.$emit('alert-message', this.$t('agenda.calendarShare.settings.shown', {0: calendar.name}), 'success');
          document.dispatchEvent(new CustomEvent('agenda-refresh-shared-calendars'));
        })
        .catch(error => {
          console.error(`cannot show the calendar ${calendar.name} again`, error);
          this.$root.$emit('alert-message', this.$t('agenda.calendarShare.error'), 'error');
        })
        .finally(() => this.showingIds = this.showingIds.filter(id => id !== calendar.calendarId));
    },
  },
};
</script>
