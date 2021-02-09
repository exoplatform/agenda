<template>
  <v-flex class="agenda-timeline-header d-flex mx-3 my-2">
    <div class="d-flex align-center">
      <a :href="agendaBaseLink" class="body-1 text-uppercase text-sub-title">
        {{ $t('agenda') }}
      </a>
      <v-badge
        v-if="datePollsCount"
        :content="datePollsCount"
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
    <v-spacer />
    <v-btn
      :title="$t('agenda.button.addEvent')"
      icon
      text
      @click="openEventForm">
      <v-icon>mdi-plus</v-icon>
    </v-btn>
  </v-flex>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    agendaBaseLink: {
      type: String,
      default: null
    },
  },
  data: () => ({
    datePollsCount:0,
  }),
  created() {
    this.refresh();
  },
  methods: {
    refresh(){
      return this.$eventService.getDatePolls().then(eventsList => this.datePollsCount = eventsList && eventsList.size || 0);
    },
    openEventForm() {
      this.$root.$emit('agenda-event-quick-form', {
        summary: '',
        startDate: new Date(),
        endDate: new Date(),
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