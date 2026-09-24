<!--
Copyright (C) 2023 eXo Platform SAS.

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
    :loading="loading"
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
            {{ $t('Notification.agenda.event.viewPoll') }}
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
    eventUrl() {
      return this.notification?.parameters?.webUrl;
    },
    eventTitle() {
      return this.notification?.parameters?.eventTitle;
    },
    avatarUrl() {
      return this.calendarAvatarUrl;
    },
    message() {
      const eventTitle = this.notification?.parameters?.eventTitle;
      const participantName = this.notification?.parameters?.participantName;
      return this.$t('Notification.agenda.event.date.vote', {
        0: `<a class="user-name font-weight-bold">${participantName}</a>`,
        1: `<span class="font-weight-bold">${eventTitle}</span>`
      });
    }
  },
};
</script>
