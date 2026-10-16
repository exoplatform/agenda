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
      <div class="mt-1">
        <v-btn
          :href="eventUrl"
          color="primary"
          elevation="0"
          small
          outlined>
          <span class="text-none">
            {{ $t('Notification.agenda.event.checkParticipants') }}
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
      return this.$t('Notification.agenda.event.capacityReached');
    },
  },
};
</script>
