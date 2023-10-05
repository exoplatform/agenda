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
    space-avatar>
    <template #actions>
      <div class="text-truncate">
        <v-icon size="14" class="me-1 mb-1">fa-calendar-alt</v-icon>
        {{ eventTitle }}
      </div>
      <div class="text-truncate my-1">
        {{ eventTime }}
      </div>
      <div class="mt-1">
        <v-btn
          class="btn primary px-2"
          outlined
          small>
          {{ $t('Notification.agenda.event.view') }}
        </v-btn>
      </div>
    </template>
  </user-notification-template>
</template>
<script>
export default {
  data: () => ({
    space: null,
  }),
  props: {
    notification: {
      type: Object,
      default: null,
    },
  },
  created() {
    console.log(this.notification);
    this.$identityService.getIdentityById(this.notification?.parameters?.ownerId).then(identity => {
      if (identity?.space) {
        this.space = identity.space;
      }
    });
  },
  computed: {
    eventUrl() {
      return this.notification?.parameters?.Url;
    },
    avatarUrl() {
      return this.space?.avatarUrl;
    },
    spaceName() {
      return this.space?.prettyName;
    },
    eventTitle() {
      return this.notification?.parameters?.eventTitle;
    },
    eventTime() {
      const startDate = this.notification?.parameters?.startDate;
      const endDate = this.notification?.parameters?.endDate;
      return `${this.dateFormatter(startDate)} - ${this.dateFormatter(endDate)}`;
    },
    message() {
      const agendaName = this.spaceName;
      return this.$t('Notification.agenda.event.reminder.inSpace', {
        0: `<a class="space-name font-weight-bold">${agendaName}</a>`
      });
    }
  },
  methods: {
    dateFormatter(completeDate) {
      if (completeDate) {
        const date = new Date(completeDate);
        let hours = date.getHours();
        let minutes = date.getMinutes();
        if (hours<10) {
          hours='0'.concat(hours);
        }
        if (minutes<10) {
          minutes='0'.concat(minutes);
        }
        return `${hours}:${minutes}`;
      }
    },
  }
};
</script>
