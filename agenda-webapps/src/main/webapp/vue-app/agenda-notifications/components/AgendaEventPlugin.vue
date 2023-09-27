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
      <div v-if="buttonText!=''" class="mt-1">
        <v-btn
          class="btn primary px-2"
          outlined
          small>
          {{ buttonText }}
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
      let message = '';
      const agendaName = this.spaceName;

      if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'ADDED') {
        message = this.$t('Notification.agenda.event.created', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'DATES_UPDATED' && this.notification?.parameters?.eventStatus === 'CONFIRMED') {
        message = this.$t('Notification.agenda.event.dates.updated', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'DATES_UPDATED' && this.notification?.parameters?.eventStatus === 'TENTATIVE'){
        message = this.$t('Notification.agenda.event.datePoll.dates.updated', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'UPDATED' && this.notification?.parameters?.eventStatus === 'TENTATIVE'){
        message = this.$t('Notification.agenda.event.datePoll.updated', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'SWITCHED_EVENT_TO_DATE_POLL') {
        message = this.$t('Notification.agenda.event.switchedToDatePoll', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'SWITCHED_DATE_POLL_TO_EVENT') {
        message = this.$t('Notification.agenda.event.switchedToEvent', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'UPDATED') {
        message = this.$t('Notification.agenda.event.updated', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else if (this.notification?.parameters?.EVENT_MODIFICATION_TYPE === 'DELETED' && this.notification?.parameters?.eventStatus === 'TENTATIVE'){
        message = this.$t('Notification.agenda.event.mail.body.datePoll.deleted', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      } else {
        message = this.$t('Notification.agenda.event.canceled', {0: `<span class="font-weight-bold">${eventTitle}</span>`, 1: `<a class="space-name font-weight-bold">${agendaName}</a>`});
      }

      return message;
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
};
</script>
