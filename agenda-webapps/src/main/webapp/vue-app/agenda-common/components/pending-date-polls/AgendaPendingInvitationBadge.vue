<template>
  <v-badge
    :value="pendingInvitationsCount"
    :content="pendingInvitationsCount"
    offset-y="10"
    class="iconBadge d-none d-md-inline"
    color="#F8B121">
    <v-btn
      :title="$t('agenda.pendingInvitations')"
      :loading="loading"
      class="ml-4 mr-2"
      color="white"
      icon
      depressed
      x-small
      @click="$root.$emit('agenda-pending-date-polls-drawer-open')">
      <i class="uiIcon darkGreyIcon uiIcon32x32 uiIconClock mb-1"></i>
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
  },
  data: () => ({
    datePollsCount: 0,
    pendingEventsCount: 0,
    loading: 0,
  }),
  computed: {
    pendingInvitationsCount() {
      return this.loading ? 0 : this.datePollsCount + this.pendingEventsCount;
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
    this.$root.$on('agenda-event-response-sent', this.countPendingEvents);
    this.$root.$on('agenda-event-saved', this.countDatePolls);
    if (this.spaceRetrieved) {
      this.countPendingInvitations();
    }
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