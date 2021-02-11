<template>
  <v-flex class="agenda-timeline-header d-flex mx-3 my-2">
    <div class="d-flex align-center">
      <a :href="agendaBaseLink" class="body-1 text-uppercase text-sub-title">
        {{ $t('agenda') }}
      </a>
      <agenda-pending-invitation-badge :current-space="currentSpace" />
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
  methods: {
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