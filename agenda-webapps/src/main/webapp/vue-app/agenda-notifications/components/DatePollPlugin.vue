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
    eventTitle() {
      return this.notification?.parameters?.eventTitle;
    },
    avatarUrl() {
      return this.space?.avatarUrl;
    },
    spaceName() {
      return this.space?.prettyName;
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
