<template>
  <div>
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
      color="red">
      <v-btn
        :title="$t('agenda.pendingInvitationTooltip')"
        class="ml-2"
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
  data: () => {
    return {
      datePollsCount: 0,
    };
  },
  created() {
    this.refresh();
  },
  methods: {
    refresh(){
      return this.$eventService.getDatePolls().then(eventsList => this.datePollsCount = eventsList && eventsList.size || 0);
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
