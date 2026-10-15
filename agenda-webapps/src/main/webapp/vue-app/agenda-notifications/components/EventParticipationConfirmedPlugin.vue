<!--
Copyright (C) 2026 eXo Platform SAS.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <user-notification-template
    :notification="notification"
    :avatar-url="avatarUrl"
    :message="message"
    :url="eventUrl"
    :space-avatar="!personalCalendar">
    <template #actions>
      <div class="text-truncate">
        <v-icon size="14" class="me-1 mb-1">fa-calendar-alt</v-icon>
        {{ eventTitle }}
      </div>
      <div v-if="datesLine" class="text-truncate my-1">
        {{ datesLine }}
      </div>
      <div class="mt-1">
        <v-btn
          :href="eventUrl"
          color="primary"
          elevation="0"
          small
          outlined>
          <span class="text-none">
            {{ $t('Notification.agenda.event.open') }}
          </span>
        </v-btn>
      </div>
    </template>
  </user-notification-template>
</template>
<script>
import calendarOwnerMixin from '../js/calendarOwnerMixin.js';

export default {
  mixins: [calendarOwnerMixin],
  props: {
    notification: {
      type: Object,
      default: null,
    },
  },
  computed: {
    parameters() {
      return this.notification?.parameters || {};
    },
    eventUrl() {
      return this.parameters.webUrl;
    },
    avatarUrl() {
      return this.calendarAvatarUrl;
    },
    eventTitle() {
      return this.parameters.eventTitle;
    },
    message() {
      return this.$t('Notification.agenda.event.participationConfirmed');
    },
    // On a recurring event, the first date promoted, then the number of the
    // other dates promoted
    datesLine() {
      if (!this.parameters.eventOccurrenceId || !this.parameters.startDate) {
        return null;
      }
      const promotedDate = this.promotedDate();
      const otherDates = Number(this.parameters.otherDates) || 0;
      if (this.parameters.openEnded === 'true') {
        return this.$t('Notification.agenda.event.participationConfirmed.followingDates', {0: promotedDate});
      } else if (otherDates === 1) {
        return this.$t('Notification.agenda.event.participationConfirmed.otherDate', {0: promotedDate});
      } else if (otherDates > 1) {
        return this.$t('Notification.agenda.event.participationConfirmed.otherDates', {0: promotedDate, 1: otherDates});
      }
      return promotedDate;
    },
  },
  methods: {
    promotedDate() {
      const language = eXo.env.portal.language;
      const dayFormat = {weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'};
      const startDate = this.parameters.startDate;
      if (this.parameters.allDay === 'true') {
        // An all-day date is stored as its day: read in the browser's zone, a
        // date without a time would be midnight UTC, the day before west of it
        const [year, month, day] = startDate.split('-').map(Number);
        return new Date(year, month - 1, day).toLocaleDateString(language, dayFormat);
      }
      const date = new Date(startDate);
      return this.$t('Notification.agenda.event.participationConfirmed.dateTime', {
        0: date.toLocaleDateString(language, dayFormat),
        1: date.toLocaleTimeString(language, {hour: '2-digit', minute: '2-digit'}),
      });
    },
  },
};
</script>
