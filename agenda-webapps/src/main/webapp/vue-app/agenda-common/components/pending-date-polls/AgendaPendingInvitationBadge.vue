<template>
  <v-badge
    v-if="datePollsCount"
    :content="datePollsCount"
    offset-y="10"
    class="d-none d-md-inline"
    color="#F8B121">
    <v-btn
      :title="$t('agenda.pendingInvitations')"
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
  }),
  computed: {
    spaceIdentityId() {
      return this.currentSpace && this.currentSpace.identity && this.currentSpace.identity.id;
    },
  },
  created() {
    this.$root.$on('agenda-refresh', this.getInComingDatePolls);
    this.$root.$on('agenda-event-saved', this.getInComingDatePolls);
    this.getInComingDatePolls();
  },
  methods: {
    getInComingDatePolls() {
      return this.$eventService.getDatePolls(this.spaceIdentityId).then(eventsList => this.datePollsCount = eventsList && eventsList.size || 0);
    },
  }
};
</script>