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
    user-avatar>
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
      return this.notification?.parameters?.participantAvatarUrl;
    },
    spaceName() {
      return this.space?.displayName;
    },
    message() {
      const eventTitle = this.notification?.parameters?.eventTitle;
      const participantName = this.notification?.parameters?.participantName;
      const responseType = this.notification?.parameters?.eventResponse;
      let status;
      if (responseType === 'ACCEPTED') {
        status='accepted';
      } else if (responseType === 'DECLINED') {
        status='declined';
      } else {
        status='mayBe';
      }
      return this.computeMessage(participantName, eventTitle, status);
    },
    buttonText() {
      let button = '';
      switch (this.notification?.parameters?.EVENT_MODIFICATION_TYPE) {
      case 'ADDED':
      case 'DATES_UPDATED':
      case 'SWITCHED_DATE_POLL_TO_EVENT':
      case 'SWITCHED_EVENT_TO_DATE_POLL' :
        button = this.$t('Notification.agenda.event.reply');
        break;
      case 'UPDATED' :
        button = this.$t('Notification.agenda.event.view');
        break;
      default:
        break;
      }
      return button;

    }
  },
  methods: {
    computeMessage(partipantName, eventTitle, status) {
      const isCreator = this.notification?.parameters?.isCreator || false;
      const agendaName = this.spaceName;
      const resourceBundleKey = 'Notification.agenda.event.'.concat(isCreator ? 'creator' : 'participant').concat('.response.').concat(status);
      return this.$t(resourceBundleKey, {
        0: `<a class="user-name font-weight-bold">${partipantName}</a>`,
        1: `<span class="font-weight-bold">${eventTitle}</span>`,
        2: `<a class="space-name font-weight-bold">${agendaName}</a>`
      });
    }
  }
};
</script>
