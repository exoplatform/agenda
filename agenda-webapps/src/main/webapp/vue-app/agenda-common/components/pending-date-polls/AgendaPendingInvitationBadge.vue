<template>
  <v-badge
    :value="datePollsCount"
    :content="datePollsCount"
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
    loading: true,
  }),
  computed: {
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
        this.countDatePolls();
      }
    },
  },
  created() {
    this.$root.$on('agenda-refresh', this.countDatePolls);
    this.$root.$on('agenda-event-saved', this.countDatePolls);
    if (this.spaceRetrieved) {
      this.countDatePolls();
    }
  },
  methods: {
    countDatePolls() {
      this.loading = true;
      return this.$eventService.countDatePolls(this.spaceIdentityId)
        .then(datePollsCount => this.datePollsCount = datePollsCount)
        .finally(() => this.loading = false);
    },
  }
};
</script>