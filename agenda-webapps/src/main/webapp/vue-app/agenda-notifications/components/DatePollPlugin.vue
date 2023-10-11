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
      <div class="mt-1">
        <v-btn
          class="btn primary px-2"
          outlined
          small>
          {{ $t('Notification.agenda.event.reply') }}
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
    if (this.notification?.space) {
      this.space = this.notification?.space;
    } else {
      this.$identityService.getIdentityById(this.notification?.parameters?.ownerId).then(identity => {
        if (identity?.space) {
          this.space = identity.space;
        }
      });
    }
  },
  computed: {
    eventUrl() {
      return this.notification?.parameters?.Url;
    },
    eventTitle() {
      return this.notification?.parameters?.eventTitle;
    },
    avatarUrl() {
      return this.space?.avatarUrl;
    },
    spaceName() {
      return this.space?.displayName;
    },
    message() {
      const eventTitle = this.notification?.parameters?.eventTitle;
      const agendaName = this.spaceName;
      const creatorName = this.notification?.parameters?.eventCreator;
      return this.$t('Notification.agenda.event.mail.body.datePoll', {
        0: `<a class="user-name font-weight-bold">${creatorName}</a>`,
        1: `<span class="font-weight-bold">${eventTitle}</span>`,
        2: `<a class="space-name font-weight-bold">${agendaName}</a>`
      });
    }
  },
};
</script>
