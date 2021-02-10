<template>
  <div class="text-no-wrap">
    <v-btn
      class="btn btn-primary"
      @click="openNewEventForm">
      <v-icon dark>
        mdi-plus
      </v-icon>
      <span class="ml-2 d-none d-lg-inline">
        {{ $t('agenda.button.addEvent') }}
      </span>
    </v-btn>
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
  </div>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
  },
  data: () => {
    return {
      datePollsCount: 0,
      ownerIds:[],
    };
  },
  created() {
    const ownerIds = this.currentSpace.identity.id;
    this.$root.$on('agenda-refresh', this.getInComingDatePolls(ownerIds));
    this.$root.$on('agenda-event-saved', this.getInComingDatePolls(ownerIds));
    this.getInComingDatePolls(ownerIds);
  },
  methods: {
    getInComingDatePolls(ownerIds) {
      return this.$eventService.getDatePolls(ownerIds).then(eventsList => this.datePollsCount = eventsList && eventsList.size || 0);
    },
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
  },
};
</script>
