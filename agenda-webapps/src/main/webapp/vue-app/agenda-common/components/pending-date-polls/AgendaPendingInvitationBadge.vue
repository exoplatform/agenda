<template>
  <v-badge
    :value="pendingInvitationsCount"
    :content="pendingInvitationsCount"
    :offset-x="offsetX"
    :offset-y="offsetY"
    overlap
    bordered
    color="#F8B121">
    <v-btn
      :title="$t('agenda.pendingInvitations')"
      icon
      @click="$root.$emit('agenda-pending-date-polls-drawer-open')">
      <v-icon>fa-history</v-icon>
    </v-btn>
  </v-badge>
</template>

<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: () => null,
    },
    offsetX: {
      type: Object,
      default: () => 18,
    },
    offsetY: {
      type: Number,
      default: () => 22,
    },
  },
  data: () => ({
    datePollsCount: 0,
    pendingEventsCount: 0,
    loading: 0,
  }),
  computed: {
    pendingInvitationsCount() {
      return this.datePollsCount + this.pendingEventsCount;
    },
    spaceRetrieved() {
      return !eXo.env.portal.spaceId || this.currentSpace;
    },
    spaceIdentityId() {
      return this.currentSpace && this.currentSpace.identity && this.currentSpace.identity.id;
    },
  },
  watch: {
    currentSpace() {
      if (this.spaceRetrieved) {
        this.countPendingInvitations();
      }
    },
  },
  created() {
    this.$root.$on('agenda-refresh', this.countPendingInvitations);
    this.$root.$on('agenda-refresh-pending', this.countPendingInvitations);
    this.$root.$on('agenda-event-response-sent', this.countPendingEvents);
    this.$root.$on('agenda-event-saved', this.countDatePolls);
    this.$root.$on('agenda-event-deleted', () => {
      window.setTimeout(() => {
        this.countPendingInvitations();
      }, 11000);
    });
    this.countPendingInvitations();
  },
  methods: {
    countPendingInvitations() {
      this.countDatePolls();
      this.countPendingEvents();
    },
    countDatePolls() {
      this.loading++;
      return this.$eventService.countDatePolls(this.spaceIdentityId)
        .then(datePollsCount => this.datePollsCount = datePollsCount)
        .finally(() => this.loading--);
    },
    countPendingEvents() {
      this.loading++;
      return this.$eventService.countPendingEvents(this.spaceIdentityId)
        .then(pendingEventsCount => this.pendingEventsCount = pendingEventsCount)
        .finally(() => this.loading--);
    },
  }
};
</script>